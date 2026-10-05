package demoday.backend.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentOrderRequest(

        @Schema(
                description = "구매할 상품 코드",
                example = "WEEK_PASS"
        )
        @NotBlank(message = "상품 코드는 필수입니다.")
        @Size(
                max = 50,
                message = "상품 코드는 50자 이하여야 합니다."
        )
        String productCode,

        @Schema(
                description = "결제 완료 후 돌아갈 허용된 프론트엔드 주소",
                example = "http://localhost:3000",
                nullable = true
        )
        String redirectUrl
) {
}
