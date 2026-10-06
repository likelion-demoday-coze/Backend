package demoday.backend.payment.dto;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.Payment;

public record PaymentConfirmResultResponse(
        Long paymentId,
        String orderNumber,
        PaymentStatus status
) {

    public static PaymentConfirmResultResponse from(Payment payment) {
        return new PaymentConfirmResultResponse(
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getStatus()
        );
    }
}
