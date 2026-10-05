package demoday.backend.payment.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KorpayConfirmResponse(

        @Schema(description = "결제 결과 코드", example = "3001")
        String resultCode,

        @Schema(description = "결제 결과 메시지", example = "성공")
        String message,

        @Schema(description = "PG 거래 고유번호")
        String tid,

        @Schema(description = "가맹점 ID 또는 통합 ID")
        String merchantId,

        @Schema(description = "주문번호")
        String orderNumber,

        @Schema(description = "상품명")
        String productName,

        @Schema(description = "통화", example = "KRW")
        String currency,

        @Schema(description = "승인 금액", example = "4900")
        Integer amount,

        @Schema(
                description = "승인 일시(yyyyMMddHHmmss)",
                example = "20261005153000"
        )
        String approvedAt,

        @Schema(
                description = "결제 수단",
                allowableValues = {
                        "card",
                        "easyPay",
                        "unified"
                },
                example = "card"
        )
        String payMethod,

        @Schema(description = "예약 필드")
        String reserved
) {

    public boolean isApproved() {
        return "3001".equals(resultCode);
    }

    public boolean isUnknown() {
        return "3004".equals(resultCode)
                || "3006".equals(resultCode)
                || "P001".equals(resultCode);
    }
}
