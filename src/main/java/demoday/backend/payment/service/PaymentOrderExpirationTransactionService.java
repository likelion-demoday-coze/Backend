package demoday.backend.payment.service;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentOrderExpirationTransactionService {

    private final PaymentRepository paymentRepository;

    @Transactional
    public boolean expireIfEligible(
            Long paymentId,
            LocalDateTime expiredBefore
    ) {
        Payment payment = paymentRepository
                .findByIdForUpdate(paymentId)
                .orElse(null);

        if (payment == null
                || (payment.getStatus() != PaymentStatus.READY
                && payment.getStatus()
                != PaymentStatus.PAYMENT_REQUESTED)
                || payment.getRequestedAt().isAfter(expiredBefore)) {
            return false;
        }

        payment.expire();
        return true;
    }
}
