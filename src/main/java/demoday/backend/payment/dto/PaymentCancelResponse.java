package demoday.backend.payment.dto;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PaymentCancelResponse(

        @Schema(description = "결제 ID", example = "1")
        Long paymentId,

        @Schema(description = "주문번호")
        String orderNumber,

        @Schema(description = "결제 상태", example = "REFUNDED")
        PaymentStatus status,

        @Schema(description = "전액 환불 금액", example = "1900")
        Integer refundedAmount,

        @Schema(description = "환불 완료 시각(KST)")
        LocalDateTime refundedAt
) {

    public static PaymentCancelResponse from(Payment payment) {
        return new PaymentCancelResponse(
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getCancelledAt()
        );
    }
}
