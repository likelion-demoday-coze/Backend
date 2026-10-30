package demoday.backend.payment.dto;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.domain.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record PaymentOrderResponse(

        @Schema(description = "내부 결제 ID", example = "1")
        Long paymentId,

        @Schema(description = "주문번호", example = "PAY20261025170000A1B2C3D4E5F6")
        String orderNumber,

        @Schema(description = "상품 코드", example = "WEEKLY_PASS")
        String productCode,

        @Schema(description = "상품명", example = "일주일 패스")
        String productName,

        @Schema(description = "결제 금액", example = "1900")
        Integer amount,

        @Schema(description = "결제 상태", example = "PAYMENT_REQUESTED")
        PaymentStatus status,

        @Schema(description = "페이앱 결제창 URL")
        String paymentUrl,

        @Schema(description = "주문 생성 시각(KST)")
        LocalDateTime requestedAt
) {

    public static PaymentOrderResponse of(
            Payment payment,
            String paymentUrl
    ) {
        return new PaymentOrderResponse(
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getOrderedProductCode(),
                payment.getOrderedProductName(),
                payment.getAmount(),
                payment.getStatus(),
                paymentUrl,
                payment.getRequestedAt()
        );
    }
}
