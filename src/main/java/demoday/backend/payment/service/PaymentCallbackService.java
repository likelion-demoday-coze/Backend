package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.KorpayClient;
import demoday.backend.payment.client.dto.KorpayConfirmResponse;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.config.KorpayProperties;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.dto.KorpayCallbackRequest;
import demoday.backend.payment.dto.PaymentConfirmResultResponse;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private static final String AUTHENTICATION_SUCCESS_CODE = "0000";

    private final PaymentRepository paymentRepository;
    private final PaymentProcessingService paymentProcessingService;
    private final KorpayClient korpayClient;
    private final KorpayProperties korpayProperties;
    private final TransactionTemplate transactionTemplate;

    public PaymentConfirmResultResponse confirm(
            KorpayCallbackRequest callback
    ) {
        // 콜백 처리에 필요한 최소 필수값 검증
        validateRequiredCallbackValues(callback);

        // 인증 실패 콜백이면 실패 상태 먼저 저장 후 예외 반환
        if (!AUTHENTICATION_SUCCESS_CODE.equals(callback.resultCode())) {
            recordAuthenticationFailure(callback);

            throw new ProjectException(
                    PaymentErrorCode.AUTHENTICATION_FAILED
            );
        }

        // 주문 잠금 조회하여 주문번호, 가맹점, 금액, paymentKey 검증
        // 검증이 끝난 paymentKey 주문에 기록하고 첫번째 트랜잭션 종료
        PreparedPayment preparedPayment = preparePayment(callback);

        // 동일 콜백 재전송되면 상품 중복 지급하지 않고 기존 결과 반환
        if (preparedPayment.completed()) {
            return findCompletedResult(
                    preparedPayment.paymentKey()
            );
        }

        // PG 승인은 저장됐지만 상품 지급이 실패한 결제는 승인 API를 다시 호출하지 않는다.
        if (preparedPayment.approved()) {
            return paymentProcessingService.retryFulfillment(
                    preparedPayment.paymentId()
            );
        }

        KorpayConfirmResponse confirmResponse;

        try {
            // 외부 PG 호출은 DB 트랜잭션 밖에서 실행
            confirmResponse = korpayClient.confirm(
                    preparedPayment.paymentKey()
            );
        } catch (ProjectException exception) {
            // 승인 여부 불명확할 수 있으므로 UNKNOWN으로 기록
            markUnknown(
                    preparedPayment.paymentKey(),
                    null,
                    exception.getMessage()
            );
            throw exception;
        } catch (RuntimeException exception) {
            // 예상치 못한 통신 오류도 승인 여부 단정 불가능
            markUnknown(
                    preparedPayment.paymentKey(),
                    null,
                    "코페이 승인 API 통신 실패"
            );

            throw new ProjectException(
                    PaymentErrorCode.PG_COMMUNICATION_FAILED
            );
        }

        // 코페이 승인 결과 검증 후 결제 상태 변경 및 상품 지급 처리
        return processConfirmResponse(
                preparedPayment.paymentKey(),
                confirmResponse
        );
    }

    private PreparedPayment preparePayment(
            KorpayCallbackRequest callback
    ) {
        return transactionTemplate.execute(status -> {
            // 같은 주문의 중복 콜백이 동시에 처리되지 않도록 결제 행 잠금
            Payment payment = paymentRepository
                    .findByOrderNumberForUpdate(
                            callback.orderNumber()
                    )
                    .orElseThrow(() ->
                            new ProjectException(
                                    PaymentErrorCode.ORDER_NOT_FOUND
                            )
                    );

            // 프론트나 콜백 요청값 신뢰하지 않고 서버 주문 정보와 비교
            validateCallback(payment, callback);

            // 이미 상품 지급까지 끝났으면 기존 결과 반환
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                return new PreparedPayment(
                        payment.getPaymentId(),
                        payment.getPaymentKey(),
                        false,
                        true
                );
            }

            // 승인까지 저장된 결제는 상품 지급 단계만 재시도한다.
            if (payment.getStatus() == PaymentStatus.APPROVED) {
                return new PreparedPayment(
                        payment.getPaymentId(),
                        payment.getPaymentKey(),
                        true,
                        false
                );
            }

            // 승인 여부가 불명확한 결제는 코페이 상태 조회 전까지 재승인하지 않는다.
            if (payment.getStatus() == PaymentStatus.UNKNOWN) {
                throw new ProjectException(
                        PaymentErrorCode.CONFIRM_RESULT_UNKNOWN
                );
            }

            // 이미 실패했거나 취소된 결제는 다시 승인 경로 진입 불가
            if (payment.getStatus() == PaymentStatus.FAILED
                    || payment.getStatus() == PaymentStatus.CANCELLED) {
                throw new ProjectException(
                        PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
                );
            }

            // 검증 통과한 paymentKey만 저장해 최종 승인 요청과 연결
            payment.recordAuthentication(
                    callback.paymentKey(),
                    callback.merchantId()
            );

            log.info(
                    "[Payment] 결제 인증 완료 - paymentId: {}, orderNumber: {}, memberId: {}, paymentKey: {}",
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    payment.getMember().getMemberId(),
                    maskPaymentKey(callback.paymentKey())
            );

            return new PreparedPayment(
                    payment.getPaymentId(),
                    callback.paymentKey(),
                    false,
                    false
            );
        });
    }

    private PaymentConfirmResultResponse processConfirmResponse(
            String paymentKey,
            KorpayConfirmResponse response
    ) {
        // 응답 자체가 없으면 UNKNOWN으로 기록
        if (response == null) {
            markUnknown(
                    paymentKey,
                    null,
                    "코페이 승인 응답이 없습니다."
            );

            throw new ProjectException(
                    PaymentErrorCode.CONFIRM_RESULT_UNKNOWN
            );
        }

        // 3004, 3006, P001은 최종 상태 불명확하므로 실패로 확정하지 않음
        if (response.isUnknown()) {
            markUnknown(
                    paymentKey,
                    response.resultCode(),
                    response.message()
            );

            throw new ProjectException(
                    PaymentErrorCode.CONFIRM_RESULT_UNKNOWN
            );
        }

        // 불확실 코드가 아닌 명확한 승인 실패만 FAILED로 기록
        if (!response.isApproved()) {
            markFailed(
                    paymentKey,
                    response.resultCode(),
                    response.message()
            );

            throw new ProjectException(
                    PaymentErrorCode.CONFIRM_FAILED
            );
        }

        try {
            return paymentProcessingService.processApprovedPayment(
                    paymentKey,
                    response
            );
        } catch (ProjectException exception) {
            // PG는 승인했지만 응답 대조에 실패했다면 READY로 두지 않고 확인 대상으로 남긴다.
            markUnknown(
                    paymentKey,
                    response.resultCode(),
                    exception.getMessage()
            );
            throw exception;
        }
    }

    private void validateCallback(
            Payment payment,
            KorpayCallbackRequest callback
    ) {
        if (!payment.getOrderNumber().equals(
                callback.orderNumber()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.ORDER_NUMBER_MISMATCH
            );
        }

        if (!korpayProperties.merchantId().equals(
                callback.merchantId()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.MERCHANT_MISMATCH
            );
        }

        if (!payment.getAmount().equals(callback.amount())) {
            throw new ProjectException(
                    PaymentErrorCode.AMOUNT_MISMATCH
            );
        }

        if (callback.paymentKey() == null
                || callback.paymentKey().isBlank()) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_KEY_MISSING
            );
        }

        paymentRepository.findByPaymentKey(callback.paymentKey())
                .filter(existing ->
                        !existing.getPaymentId()
                                .equals(payment.getPaymentId())
                )
                .ifPresent(existing -> {
                    throw new ProjectException(
                            PaymentErrorCode.DUPLICATE_TRANSACTION
                    );
                });
    }

    private void recordAuthenticationFailure(
            KorpayCallbackRequest callback
    ) {
        if (callback.orderNumber() == null
                || callback.orderNumber().isBlank()) {
            return;
        }

        transactionTemplate.executeWithoutResult(status ->
                paymentRepository.findByOrderNumberForUpdate(
                                callback.orderNumber()
                        )
                        .ifPresent(payment -> {
                            // 실패 콜백도 임의 요청으로 주문 상태를 바꾸지 못하도록 주문 정보와 대조한다.
                            if (!korpayProperties.merchantId().equals(
                                    callback.merchantId()
                            )) {
                                throw new ProjectException(
                                        PaymentErrorCode.MERCHANT_MISMATCH
                                );
                            }

                            if (!payment.getAmount().equals(
                                    callback.amount()
                            )) {
                                throw new ProjectException(
                                        PaymentErrorCode.AMOUNT_MISMATCH
                                );
                            }

                            if (payment.getStatus()
                                    == PaymentStatus.READY) {
                                payment.fail(
                                        callback.resultCode(),
                                        callback.message()
                                );
                            }
                        })
        );
    }

    private void markFailed(
            String paymentKey,
            String resultCode,
            String message
    ) {
        transactionTemplate.executeWithoutResult(status ->
                paymentRepository.findByPaymentKeyForUpdate(paymentKey)
                        .filter(payment ->
                                payment.getStatus() == PaymentStatus.READY
                                        || payment.getStatus() == PaymentStatus.UNKNOWN
                        )
                        .ifPresent(payment -> payment.fail(
                                        resultCode,
                                        message
                                ))
        );
    }

    private void markUnknown(
            String paymentKey,
            String resultCode,
            String message
    ) {
        transactionTemplate.executeWithoutResult(status ->
                paymentRepository.findByPaymentKeyForUpdate(paymentKey)
                        .filter(payment ->
                                payment.getStatus() == PaymentStatus.READY
                                        || payment.getStatus() == PaymentStatus.UNKNOWN
                        )
                        .ifPresent(payment -> payment.markUnknown(
                                        resultCode,
                                        message
                                ))
        );
    }

    private PaymentConfirmResultResponse findCompletedResult(
            String paymentKey
    ) {
        Payment payment = paymentRepository.findByPaymentKey(paymentKey)
                .orElseThrow(() ->
                        new ProjectException(
                                PaymentErrorCode.ORDER_NOT_FOUND
                        )
                );

        return PaymentConfirmResultResponse.from(payment);
    }

    private void validateRequiredCallbackValues(
            KorpayCallbackRequest callback
    ) {
        if (callback == null
                || callback.resultCode() == null
                || callback.resultCode().isBlank()
                || callback.orderNumber() == null
                || callback.orderNumber().isBlank()
                || callback.merchantId() == null
                || callback.merchantId().isBlank()
                || callback.amount() == null) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_CALLBACK
            );
        }
    }

    private String maskPaymentKey(String paymentKey) {
        if (paymentKey == null || paymentKey.length() <= 8) {
            return "****";
        }

        return paymentKey.substring(0, 4)
                + "****"
                + paymentKey.substring(
                paymentKey.length() - 4
        );
    }

    private record PreparedPayment(
            Long paymentId,
            String paymentKey,
            boolean approved,
            boolean completed
    ) {
    }
}
