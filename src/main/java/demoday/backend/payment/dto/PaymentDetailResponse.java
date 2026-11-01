package demoday.backend.payment.dto;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.domain.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PaymentDetailResponse(
        @Schema(description = "결제 ID", example = "1")
        Long paymentId,
        @Schema(description = "주문번호", example = "PAY20261031120000ABCDEF")
        String orderNumber,
        @Schema(description = "주문 당시 상품 코드", example = "WEEKLY_PASS")
        String productCode,
        @Schema(description = "주문 당시 상품명", example = "1주일 패스")
        String productName,
        @Schema(description = "결제 금액", example = "1900")
        Integer amount,
        @Schema(description = "결제 상태")
        PaymentStatus status,

        @Schema(description = "결제 승인 일시")
        LocalDateTime paidAt,
        @Schema(description = "결제 수단", example = "card")
        String payMethod,
        @Schema(description = "카드사명", example = "신한카드")
        String cardName,

        @Schema(description = "패스 시작 일시")
        LocalDateTime passStartedAt,
        @Schema(description = "패스 만료 일시")
        LocalDateTime passExpiresAt,

        @Schema(description = "환불 신청 일시")
        LocalDateTime cancellationRequestedAt,
        @Schema(description = "환불 완료 일시")
        LocalDateTime refundedAt,
        @Schema(description = "환불 금액")
        Integer refundedAmount,

        @Schema(description = "페이앱 영수증 URL")
        String receiptUrl,
        @Schema(description = "현재 환불 신청 가능 여부")
        boolean refundable
) {
    public static PaymentDetailResponse from(
            Payment payment,
            MemberPass memberPass,
            boolean refundable
    ) {
        boolean refunded = payment.getStatus() == PaymentStatus.REFUNDED;

        return new PaymentDetailResponse(
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getOrderedProductCode(),
                payment.getOrderedProductName(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getApprovedAt(),
                payment.getPayMethod(),
                payment.getCardName(),
                memberPass == null ? null : memberPass.getStartedAt(),
                memberPass == null ? null : memberPass.getExpiresAt(),
                payment.getCancellationRequestedAt(),
                refunded ? payment.getCancelledAt() : null,
                refunded ? payment.getAmount() : null,
                payment.getReceiptUrl(),
                refundable
        );
    }
}
