package demoday.backend.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record KorpayCallbackRequest(

        @NotBlank
        String resultCode,

        String message,

        String paymentKey,

        @NotBlank
        String merchantId,

        @NotBlank
        String orderNumber,

        @NotNull
        Integer amount,

        String reserved
) {
}
