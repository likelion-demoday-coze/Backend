package demoday.backend.payment.dto;

import demoday.backend.payment.domain.Payment;
import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentOrderResponse(

        @Schema(
                description = "내부 결제 ID",
                example = "1"
        )
        Long paymentId,

        @Schema(
                description = "코페이 가맹점 ID",
                example = "test12345m"
        )
        String merchantId,

        @Schema(
                description = "주문번호",
                example = "PAY202610051530001A2B3C"
        )
        String orderNumber,

        @Schema(
                description = "상품명",
                example = "일주일 패스"
        )
        String productName,

        @Schema(
                description = "결제 금액",
                example = "4900"
        )
        Integer amount,

        @Schema(
                description = "결제 수단",
                allowableValues = "card",
                example = "card"
        )
        String payMethod,

        @Schema(
                description = "코페이 인증 결과 수신 URL",
                example = "https://api.example.com/api/v1/payments/callback"
        )
        String returnUrl,

        @Schema(
                description = "전문 생성 일시(yyyyMMddHHmmss)",
                example = "20261005153000"
        )
        String ediDate,

        @Schema(
                description = "결제창 호출용 위변조 검증 해시"
        )
        String hashKey,

        @Schema(
                description = "코페이 reserved 필드에 그대로 전달할 프론트엔드 주소",
                example = "http://localhost:3000"
        )
        String reserved
) {

    public static PaymentOrderResponse of(
            Payment payment,
            String merchantId,
            String payMethod,
            String returnUrl,
            String hashKey,
            String reserved
    ) {
        return new PaymentOrderResponse(
                payment.getPaymentId(),
                merchantId,
                payment.getOrderNumber(),
                payment.getProduct().getName(),
                payment.getAmount(),
                payMethod,
                returnUrl,
                payment.getEdiDate(),
                hashKey,
                reserved
        );
    }
}
