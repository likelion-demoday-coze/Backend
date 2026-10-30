package demoday.backend.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PaymentOrderRequest(

        @Schema(description = "결제 상품 코드", example = "WEEKLY_PASS")
        @NotBlank
        @Size(max = 50)
        String productCode,

        @Schema(
                description = "결제 안내를 받을 휴대전화 번호(숫자만)",
                example = "01012345678"
        )
        @NotBlank
        @Pattern(regexp = "^01[016789]\\d{7,8}$")
        String receiverPhone
) {
}
