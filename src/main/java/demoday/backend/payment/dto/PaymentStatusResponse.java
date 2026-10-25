package demoday.backend.payment.dto;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PaymentStatusResponse(

        @Schema(description = "내부 결제 ID", example = "1")
        Long paymentId,

        @Schema(
                description = "주문번호",
                example = "PAY20261006123000A1B2C3D4E5F6"
        )
        String orderNumber,

        @Schema(description = "상품 코드", example = "WEEK_PASS")
        String productCode,

        @Schema(description = "상품명", example = "일주일 패스")
        String productName,

        @Schema(description = "결제 금액", example = "1000")
        Integer amount,

        @Schema(
                description = "결제 상태(READY, PAYMENT_REQUESTED, APPROVED, COMPLETED, CANCEL_REQUESTED, REFUNDED, FAILED, CANCELLED)",
                example = "COMPLETED"
        )
        PaymentStatus status,

        @Schema(description = "주문 생성 시각")
        LocalDateTime requestedAt,

        @Schema(description = "PG 승인 시각")
        LocalDateTime approvedAt,

        @Schema(description = "상품 지급 완료 시각")
        LocalDateTime completedAt,

        @Schema(description = "결제 취소 또는 환불 완료 시각")
        LocalDateTime cancelledAt,

        @Schema(description = "실패 또는 확인 필요 사유")
        String failureMessage
) {

    public static PaymentStatusResponse from(Payment payment) {
        return new PaymentStatusResponse(
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getOrderedProductCode(),
                payment.getOrderedProductName(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getRequestedAt(),
                payment.getApprovedAt(),
                payment.getCompletedAt(),
                payment.getCancelledAt(),
                payment.getFailureMessage()
        );
    }
}
