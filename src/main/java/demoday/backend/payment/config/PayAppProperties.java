package demoday.backend.payment.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.payment.payapp")
public record PayAppProperties(
        @NotBlank
        String userId,

        @NotBlank
        String linkKey,

        @NotBlank
        String linkValue,

        @NotBlank
        String baseUrl,

        @NotBlank
        @Pattern(regexp = "^/.*")
        String requestPath,

        @NotBlank
        String feedbackUrl,

        @NotBlank
        String returnUrl,

        @NotBlank
        @Pattern(regexp = "card")
        String openPayType,

        @NotNull
        Duration connectTimeout,

        @NotNull
        Duration readTimeout
) {
}
