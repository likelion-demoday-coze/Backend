package demoday.backend.payment.service;

import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.KorpayClient;
import demoday.backend.payment.client.dto.KorpayConfirmResponse;
import demoday.backend.payment.code.PassType;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.config.KorpayProperties;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.domain.Product;
import demoday.backend.payment.dto.KorpayCallbackRequest;
import demoday.backend.payment.dto.PaymentConfirmResultResponse;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackService {

    private static final String AUTHENTICATION_SUCCESS_CODE = "0000";

    private static final DateTimeFormatter APPROVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository paymentRepository;
    private final MemberPassRepository memberPassRepository;
    private final FishService fishService;
    private final KorpayClient korpayClient;
    private final KorpayProperties korpayProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

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
                        payment.getPaymentKey(),
                        true
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
                    callback.paymentKey(),
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

        return transactionTemplate.execute(status -> {
            // 승인 완료 처리와 상품 지급 직렬화 위해 결제 행 다시 잠금
            Payment payment = paymentRepository
                    .findByPaymentKeyForUpdate(paymentKey)
                    .orElseThrow(() ->
                            new ProjectException(
                                    PaymentErrorCode.ORDER_NOT_FOUND
                            )
                    );

            // 동일 승인 결과 재전송돼도 상품 재지급하지 않음
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                return PaymentConfirmResultResponse.from(payment);
            }

            // 검증 통해 위변조 및 잘못된 주문 연결 방지
            validateConfirmResponse(payment, response);

            LocalDateTime approvedAt =
                    parseApprovedAt(response.approvedAt());

            // 코페이 승인 정보를 내부 결제에 먼저 반영
            payment.approve(
                    response.tid(),
                    response.resultCode(),
                    response.payMethod(),
                    approvedAt
            );

            // 결제 승인 저장과 상품 지급은 같은 트랜잭션
            issueProduct(payment, approvedAt);

            LocalDateTime completedAt =
                    LocalDateTime.now(clock)
                            .truncatedTo(ChronoUnit.MICROS);

            // 상품 지급까지 성공했을 때만 COMPLETE
            payment.complete(completedAt);

            log.info(
                    "[Payment] 결제 승인 및 상품 지급 완료 - paymentId: {}, orderNumber: {}, memberId: {}, tid: {}, amount: {}",
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    payment.getMember().getMemberId(),
                    maskTid(response.tid()),
                    payment.getAmount()
            );

            return PaymentConfirmResultResponse.from(payment);
        });
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

    private void validateConfirmResponse(
            Payment payment,
            KorpayConfirmResponse response
    ) {
        if (!korpayProperties.merchantId().equals(
                response.merchantId()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.MERCHANT_MISMATCH
            );
        }

        if (!payment.getOrderNumber().equals(
                response.orderNumber()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.ORDER_NUMBER_MISMATCH
            );
        }

        if (!payment.getAmount().equals(response.amount())) {
            throw new ProjectException(
                    PaymentErrorCode.AMOUNT_MISMATCH
            );
        }

        if (!payment.getProduct().getName().equals(
                response.productName()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.PRODUCT_MISMATCH
            );
        }

        if (!"KRW".equals(response.currency())) {
            throw new ProjectException(
                    PaymentErrorCode.AMOUNT_MISMATCH
            );
        }

        if (response.tid() == null
                || response.tid().isBlank()) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_CALLBACK
            );
        }

        paymentRepository.findByTid(response.tid())
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

    private void issueProduct(
            Payment payment,
            LocalDateTime approvedAt
    ) {
        Product product = payment.getProduct();
        Long memberId = payment.getMember().getMemberId();

        if (product.getProductType() == ProductType.FISH) {
            Integer fishAmount = product.getFishAmount();

            if (fishAmount == null || fishAmount <= 0) {
                throw new ProjectException(
                        PaymentErrorCode.FULFILLMENT_FAILED
                );
            }

            // paymentId 기반 멱등 키 사용
            fishService.credit(
                    memberId,
                    fishAmount.longValue(),
                    FishTransactionType.PAID_CHARGE,
                    payment.getPaymentId(),
                    "PAYMENT:" + payment.getPaymentId()
            );

            return;
        }

        if (product.getProductType() == ProductType.PASS) {
            // 일주일 패스의 회원당 1회 구매 정책을 지급 직전에 다시 검증
            if (memberPassRepository.existsByMemberMemberId(memberId)) {
                throw new ProjectException(
                        PaymentErrorCode.PASS_ALREADY_PURCHASED
                );
            }

            Integer durationHours =
                    product.getPassDurationHours();

            if (durationHours == null || durationHours <= 0) {
                throw new ProjectException(
                        PaymentErrorCode.FULFILLMENT_FAILED
                );
            }

            // 실제 PG 승인 시각을 패스 시작 시각으로 사용
            memberPassRepository.save(
                    MemberPass.create(
                            payment.getMember(),
                            payment,
                            PassType.SEVEN_DAY,
                            approvedAt,
                            approvedAt.plusHours(durationHours)
                    )
            );

            return;
        }

        throw new ProjectException(
                PaymentErrorCode.FULFILLMENT_FAILED
        );
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
                        .ifPresent(payment ->
                                payment.fail(
                                        resultCode,
                                        message
                                )
                        )
        );
    }

    private void markUnknown(
            String paymentKey,
            String resultCode,
            String message
    ) {
        transactionTemplate.executeWithoutResult(status ->
                paymentRepository.findByPaymentKeyForUpdate(paymentKey)
                        .ifPresent(payment ->
                                payment.markUnknown(
                                        resultCode,
                                        message
                                )
                        )
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

    private LocalDateTime parseApprovedAt(String approvedAt) {
        try {
            return LocalDateTime.parse(
                    approvedAt,
                    APPROVED_AT_FORMAT
            ).truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_CALLBACK
            );
        }
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

    private String maskTid(String tid) {
        if (tid == null || tid.length() <= 6) {
            return "****";
        }

        return tid.substring(0, 3)
                + "****"
                + tid.substring(tid.length() - 3);
    }

    private record PreparedPayment(
            String paymentKey,
            boolean completed
    ) {
    }
}
