package demoday.backend.payment.dto;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.Payment;

import java.time.LocalDateTime;

public record PaymentHistoryResponse(
        Long paymentId,
        String orderNumber,
        String productCode,
        String productName,
        Integer amount,
        PaymentStatus status,
        LocalDateTime requestedAt,
        LocalDateTime approvedAt
) {

    public static PaymentHistoryResponse from(Payment payment) {
        return new PaymentHistoryResponse(
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getOrderedProductCode(),
                payment.getOrderedProductName(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getRequestedAt(),
                payment.getApprovedAt()
        );
    }
}
