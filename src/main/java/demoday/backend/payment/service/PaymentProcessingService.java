package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.dto.KorpayConfirmResponse;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.config.KorpayProperties;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.dto.PaymentConfirmResultResponse;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private static final long CONFIRMATION_STALE_MINUTES = 10L;

    private static final DateTimeFormatter APPROVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository payments;
    private final PaymentFulfillmentService fulfillmentService;
    private final KorpayProperties korpayProperties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public PaymentConfirmResultResponse processApprovedPayment(
            String paymentKey,
            KorpayConfirmResponse response
    ) {
        // PG 승인 정보를 먼저 별도 트랜잭션으로 저장
        Long paymentId = saveApproval(
                paymentKey,
                response
        );

        // 승인된 결제의 상품 지급을 별도 트랜잭션에서 처리
        return fulfillApprovedPayment(paymentId);
    }

    public PaymentConfirmResultResponse retryFulfillment(
            Long paymentId
    ) {
        // PG 승인 API를 다시 호출하지 않고 내부 상품 지급만 재시도한다.
        return fulfillApprovedPayment(paymentId);
    }

    public void retryPendingFulfillments() {
        List<Long> paymentIds = payments.findIdsByStatus(
                PaymentStatus.APPROVED
        );

        for (Long paymentId : paymentIds) {
            try {
                retryFulfillment(paymentId);
            } catch (RuntimeException exception) {
                // 한 건의 지급 실패가 나머지 승인 건 복구를 막지 않도록 개별 처리한다.
                log.error(
                        "[Payment] 미지급 승인 건 재처리 실패 - paymentId: {}",
                        paymentId,
                        exception
                );
            }
        }
    }

    public void markStaleConfirmationsUnknown() {
        LocalDateTime threshold = LocalDateTime.now(clock)
                .minusMinutes(CONFIRMATION_STALE_MINUTES)
                .truncatedTo(ChronoUnit.MICROS);

        List<Long> paymentIds =
                payments.findIdsByStatusAndConfirmationStartedAtBefore(
                        PaymentStatus.CONFIRMING,
                        threshold
                );

        for (Long paymentId : paymentIds) {
            Boolean transitioned = transactionTemplate.execute(status -> {
                Payment payment = payments.findByIdForUpdate(paymentId)
                        .orElse(null);

                if (payment == null
                        || payment.getStatus() != PaymentStatus.CONFIRMING
                        || payment.getConfirmationStartedAt() == null
                        || payment.getConfirmationStartedAt().isAfter(threshold)) {
                    return false;
                }

                payment.markUnknown(
                        null,
                        "승인 요청 처리 중단 가능성이 있어 PG 거래 확인이 필요합니다."
                );
                return true;
            });

            if (Boolean.TRUE.equals(transitioned)) {
                log.warn(
                        "[Payment] 장시간 승인 처리 건 UNKNOWN 전환 - paymentId: {}",
                        paymentId
                );
            }
        }
    }

    private Long saveApproval(
            String paymentKey,
            KorpayConfirmResponse response
    ) {
        try {
            return transactionTemplate.execute(status -> {
                Payment payment = payments
                        .findByPaymentKeyForUpdate(paymentKey)
                        .orElseThrow(() ->
                                new ProjectException(
                                        PaymentErrorCode.ORDER_NOT_FOUND
                                )
                        );

                // 중복 콜백에서도 이번 승인 응답이 기존 주문과 같은 거래인지 검증한다.
                validateApprovedResponse(payment, response);

                // 이미 완료됐거나 승인 정보가 저장된 경우에는 PG 승인 정보를 다시 기록하지 않음
                if (payment.getStatus() == PaymentStatus.COMPLETED
                        || payment.getStatus()
                        == PaymentStatus.APPROVED) {
                    if (!payment.getTid().equals(response.tid())) {
                        throw new ProjectException(
                                PaymentErrorCode.DUPLICATE_TRANSACTION
                        );
                    }
                    return payment.getPaymentId();
                }

                if (payment.getStatus() == PaymentStatus.FAILED
                        || payment.getStatus()
                        == PaymentStatus.CANCELLED) {
                    throw new ProjectException(
                            PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
                    );
                }

                payment.approve(
                        response.tid(),
                        response.resultCode(),
                        response.payMethod(),
                        parseApprovedAt(response.approvedAt())
                );

                log.info(
                        "[Payment] 승인 정보 저장 완료 - paymentId: {}, orderNumber: {}, memberId: {}, amount: {}",
                        payment.getPaymentId(),
                        payment.getOrderNumber(),
                        payment.getMember().getMemberId(),
                        payment.getAmount()
                );

                return payment.getPaymentId();
            });
        } catch (DataIntegrityViolationException exception) {
            // paymentKey 또는 tid 유니크 제약 위반도 중복 승인 거래로 변환
            throw new ProjectException(
                    PaymentErrorCode.DUPLICATE_TRANSACTION
            );
        }
    }

    private PaymentConfirmResultResponse fulfillApprovedPayment(
            Long paymentId
    ) {
        try {
            return transactionTemplate.execute(status -> {
                Payment payment = payments
                        .findByIdForUpdate(paymentId)
                        .orElseThrow(() ->
                                new ProjectException(
                                        PaymentErrorCode.ORDER_NOT_FOUND
                                )
                        );

                // 상품을 다시 지급하지 않고 기존 결과를 반환
                if (payment.getStatus()
                        == PaymentStatus.COMPLETED) {
                    return PaymentConfirmResultResponse.from(payment);
                }

                // 지급 재처리는 PG 승인이 확정된 결제만 허용
                if (payment.getStatus()
                        != PaymentStatus.APPROVED) {
                    throw new ProjectException(
                            PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
                    );
                }

                fulfillmentService.fulfill(
                        payment,
                        payment.getApprovedAt()
                );

                payment.clearFailureMessage();
                payment.complete(
                        LocalDateTime.now(clock)
                                .truncatedTo(ChronoUnit.MICROS)
                );

                log.info(
                        "[Payment] 상품 지급 완료 - paymentId: {}, orderNumber: {}, memberId: {}",
                        payment.getPaymentId(),
                        payment.getOrderNumber(),
                        payment.getMember().getMemberId()
                );

                return PaymentConfirmResultResponse.from(payment);
            });
        } catch (ProjectException exception) {
            recordFulfillmentFailure(
                    paymentId,
                    exception.getMessage()
            );

            throw exception;
        } catch (RuntimeException exception) {
            recordFulfillmentFailure(
                    paymentId,
                    "상품 지급 처리 중 내부 오류가 발생했습니다."
            );

            throw new ProjectException(
                    PaymentErrorCode.FULFILLMENT_FAILED
            );
        }
    }

    private void recordFulfillmentFailure(
            Long paymentId,
            String failureMessage
    ) {
        // 상품 지급 트랜잭션은 이미 롤백됐으므로 실패 사유는 새로운 트랜잭션에서 별도로 기록
        transactionTemplate.executeWithoutResult(status ->
                payments.findByIdForUpdate(paymentId)
                        .filter(payment ->
                                payment.getStatus()
                                        == PaymentStatus.APPROVED
                        )
                        .ifPresent(payment ->
                                payment.recordFulfillmentFailure(
                                        failureMessage
                                )
                        )
        );

        log.error(
                "[Payment] 상품 지급 실패 - paymentId: {}, reason: {}",
                paymentId,
                failureMessage
        );
    }

    private void validateApprovedResponse(
            Payment payment,
            KorpayConfirmResponse response
    ) {
        if (response == null || !response.isApproved()) {
            throw new ProjectException(
                    PaymentErrorCode.CONFIRM_FAILED
            );
        }

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

        if (!payment.getOrderedProductName().equals(
                response.productName()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.PRODUCT_MISMATCH
            );
        }

        if (!"KRW".equals(response.currency())) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_CALLBACK
            );
        }

        if (response.tid() == null
                || response.tid().isBlank()) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_CALLBACK
            );
        }
    }

    private LocalDateTime parseApprovedAt(String value) {
        try {
            return LocalDateTime.parse(
                    value,
                    APPROVED_AT_FORMAT
            ).truncatedTo(ChronoUnit.MICROS);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_CALLBACK
            );
        }
    }
}
