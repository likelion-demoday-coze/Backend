package demoday.backend.payment.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.payment.order")
public record PaymentOrderPolicyProperties(
        @NotNull Duration expiration,
        @Positive long expirationScanDelayMs
) {

    public PaymentOrderPolicyProperties {
        if (expiration != null
                && (expiration.isZero() || expiration.isNegative())) {
            throw new IllegalArgumentException(
                    "결제 주문 유효 시간은 0보다 커야 합니다."
            );
        }
    }
}
