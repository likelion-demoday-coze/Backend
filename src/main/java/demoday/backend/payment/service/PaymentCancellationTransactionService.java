package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.dto.PaymentCancelResponse;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentCancellationTransactionService {

    private final PaymentRepository paymentRepository;
    private final MemberPassRepository memberPassRepository;

    @Transactional
    public CancellationPreparation prepare(
            Long memberId,
            Long paymentId,
            LocalDateTime now
    ) {
        Payment payment = paymentRepository
                .findByPaymentIdAndMemberIdForUpdate(
                        paymentId,
                        memberId
                )
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return CancellationPreparation.alreadyRefunded(payment);
        }

        if (payment.getStatus()
                == PaymentStatus.CANCEL_REQUESTED) {
            throw new ProjectException(
                    PaymentErrorCode.CANCELLATION_IN_PROGRESS
            );
        }

        if (payment.getStatus() != PaymentStatus.APPROVED
                && payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_NOT_CANCELLABLE
            );
        }

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            MemberPass memberPass = memberPassRepository
                    .findByPaymentPaymentId(paymentId)
                    .orElseThrow(() -> new ProjectException(
                            PaymentErrorCode.FULFILLMENT_FAILED
                    ));

            if (!memberPass.isActiveAt(now)) {
                throw new ProjectException(
                        PaymentErrorCode.PASS_ALREADY_EXPIRED
                );
            }
        }

        if (payment.getProviderTransactionId() == null
                || payment.getProviderTransactionId().isBlank()) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_NOT_CANCELLABLE
            );
        }

        PaymentStatus previousStatus =
                payment.requestCancellation();

        return CancellationPreparation.requested(
                payment,
                previousStatus
        );
    }

    @Transactional
    public void restoreAfterRejection(
            Long paymentId,
            PaymentStatus previousStatus,
            String failureMessage
    ) {
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        if (payment.getStatus()
                == PaymentStatus.CANCEL_REQUESTED) {
            payment.restoreAfterCancellationFailure(previousStatus);
            payment.recordCancellationFailure(failureMessage);
        }
    }

    @Transactional
    public PaymentCancelResponse completeRefund(
            Long paymentId,
            LocalDateTime refundedAt
    ) {
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return PaymentCancelResponse.from(payment);
        }

        memberPassRepository.findByPaymentPaymentId(paymentId)
                .filter(memberPass ->
                        memberPass.getStatus()
                        == demoday.backend.payment.code.PassStatus.ACTIVE
                )
                .ifPresent(memberPass ->
                        memberPass.cancel(refundedAt)
                );

        payment.refund(refundedAt);
        return PaymentCancelResponse.from(payment);
    }

    public record CancellationPreparation(
            Long paymentId,
            String orderNumber,
            String providerTransactionId,
            PaymentStatus previousStatus,
            PaymentCancelResponse existingResult
    ) {

        static CancellationPreparation requested(
                Payment payment,
                PaymentStatus previousStatus
        ) {
            return new CancellationPreparation(
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    payment.getProviderTransactionId(),
                    previousStatus,
                    null
            );
        }

        static CancellationPreparation alreadyRefunded(
                Payment payment
        ) {
            return new CancellationPreparation(
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    payment.getProviderTransactionId(),
                    PaymentStatus.REFUNDED,
                    PaymentCancelResponse.from(payment)
            );
        }

        public boolean alreadyRefunded() {
            return existingResult != null;
        }
    }
}
