package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.dto.PayAppFeedbackRequest;
import demoday.backend.payment.logging.PaymentLogSupport;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingTransactionService {

    private static final ZoneId KST =
            ZoneId.of("Asia/Seoul");

    private static final String SUCCESS_RESULT_CODE = "4";
    private static final String CARD_PAY_METHOD = "CARD";

    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService fulfillmentService;
    private final Clock clock;

    @Transactional
    public ApprovalProcessingResult recordApproval(
            PayAppFeedbackRequest request,
            LocalDateTime approvedAt
    ) {
        Payment payment = paymentRepository
                .findByOrderNumberForUpdate(
                        request.orderNumber()
                )
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        validatePaymentInformation(payment, request);

        // 동일 Feedback 재수신 시 승인 정보를 다시 변경하지 않음
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return ApprovalProcessingResult.duplicate(
                    payment.getPaymentId()
            );
        }

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            return ApprovalProcessingResult.fulfillment(
                    payment.getPaymentId()
            );
        }

        if (payment.getStatus() == PaymentStatus.CANCEL_REQUESTED) {
            return ApprovalProcessingResult.duplicate(
                    payment.getPaymentId()
            );
        }

        if (payment.isExpiredOrder()) {
            payment.approveLateAndRequestCancellation(
                    request.transactionId(),
                    SUCCESS_RESULT_CODE,
                    CARD_PAY_METHOD,
                    approvedAt
            );

            log.warn(
                    "[Payment] 만료 후 결제 승인 감지 - paymentId: {}, orderNumber: {}, transactionId: {}",
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    PaymentLogSupport.maskTransactionId(
                            request.transactionId()
                    )
            );

            return ApprovalProcessingResult.lateRefund(
                    payment.getPaymentId(),
                    request.transactionId()
            );
        }

        if (payment.getStatus() != PaymentStatus.READY
                && payment.getStatus()
                != PaymentStatus.PAYMENT_REQUESTED) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
            );
        }

        payment.approve(
                request.transactionId(),
                SUCCESS_RESULT_CODE,
                CARD_PAY_METHOD,
                approvedAt
        );

        log.info(
                "[Payment] 결제 승인 저장 완료 - paymentId: {}, orderNumber: {}, memberId: {}, transactionId: {}, amount: {}, status: {}",
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getMember().getMemberId(),
                PaymentLogSupport.maskTransactionId(
                        request.transactionId()
                ),
                payment.getAmount(),
                payment.getStatus()
        );

        return ApprovalProcessingResult.fulfillment(
                payment.getPaymentId()
        );
    }

    @Transactional
    public void recordUnpaidCancellation(
            PayAppFeedbackRequest request
    ) {
        Payment payment = paymentRepository
                .findByOrderNumberForUpdate(request.orderNumber())
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        validateTransactionAndAmount(payment, request);

        if (payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.READY
                && payment.getStatus()
                != PaymentStatus.PAYMENT_REQUESTED) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
            );
        }

        payment.fail(
                request.payState(),
                "결제 요청이 완료 전에 취소되었습니다."
        );
    }

    @Transactional
    public void fulfill(Long paymentId) {
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        // Feedback 재전송과 스케줄러 재처리가 동시에 실행돼도 COMPLETED 상태라면 추가 지급 없이 종료
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            return;
        }

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
            );
        }

        try {
            fulfillmentService.fulfill(
                    payment,
                    payment.getApprovedAt()
            );

            LocalDateTime completedAt =
                    LocalDateTime.now(clock.withZone(KST))
                            .truncatedTo(ChronoUnit.MICROS);

            payment.complete(completedAt);

            log.info(
                    "[Payment] 상품 지급 완료 - paymentId: {}, orderNumber: {}, memberId: {}, status: {}",
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    payment.getMember().getMemberId(),
                    payment.getStatus()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[Payment] 상품 지급 실패 - paymentId: {}, orderNumber: {}, memberId: {}",
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    payment.getMember().getMemberId(),
                    exception
            );

            // 지급 트랜잭션은 롤백하고 호출자가 별도 트랜잭션으로 실패 사유를 기록한다.
            throw exception;
        }
    }

    private void validatePaymentInformation(
            Payment payment,
            PayAppFeedbackRequest request
    ) {
        int feedbackAmount;

        try {
            feedbackAmount = Integer.parseInt(
                    request.price()
            );
        } catch (NumberFormatException exception) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_FEEDBACK
            );
        }

        boolean transactionMatches =
                payment.getProviderTransactionId() == null
                        || payment.getProviderTransactionId().equals(
                        request.transactionId()
                );

        if (!transactionMatches
                || payment.getAmount() != feedbackAmount
                || !payment.getOrderedProductName().equals(
                request.productName()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_INFORMATION_MISMATCH
            );
        }
    }

    private void validateTransactionAndAmount(
            Payment payment,
            PayAppFeedbackRequest request
    ) {
        int feedbackAmount;

        try {
            feedbackAmount = Integer.parseInt(request.price());
        } catch (NumberFormatException exception) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_FEEDBACK
            );
        }

        if (payment.getProviderTransactionId() == null
                || !payment.getProviderTransactionId().equals(
                request.transactionId()
        ) || payment.getAmount() != feedbackAmount) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_INFORMATION_MISMATCH
            );
        }
    }

    @Transactional
    public void recordFulfillmentFailure(
            Long paymentId,
            String failureMessage
    ) {
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        if (payment.getStatus() == PaymentStatus.APPROVED) {
            payment.recordFulfillmentFailure(
                    failureMessage
            );
        }
    }

    public record ApprovalProcessingResult(
            Long paymentId,
            boolean fulfillmentRequired,
            boolean lateRefundRequired,
            boolean duplicateFeedback,
            String providerTransactionId
    ) {

        static ApprovalProcessingResult fulfillment(Long paymentId) {
            return new ApprovalProcessingResult(
                    paymentId, true, false, false, null
            );
        }

        static ApprovalProcessingResult lateRefund(
                Long paymentId,
                String providerTransactionId
        ) {
            return new ApprovalProcessingResult(
                    paymentId,
                    false,
                    true,
                    false,
                    providerTransactionId
            );
        }

        static ApprovalProcessingResult duplicate(
                Long paymentId
        ) {
            return new ApprovalProcessingResult(
                    paymentId, false, false, true, null
            );
        }
    }
}
