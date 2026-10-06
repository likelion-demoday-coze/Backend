package demoday.backend.payment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.payment.korpay")
public record KorpayProperties(

        @NotBlank
        @Size(max = 10)
        String merchantId,

        @NotBlank
        String merchantKey,

        @NotBlank
        String baseUrl,

        @NotBlank
        String returnUrl,

        @NotBlank
        @Pattern(regexp = "card")
        String payMethod,

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout
) {
}
