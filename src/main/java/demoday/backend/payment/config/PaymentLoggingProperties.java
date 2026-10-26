package demoday.backend.payment.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.payment.logging")
public record PaymentLoggingProperties(
        @NotNull Duration fulfillmentFailureThrottle
) {

    public PaymentLoggingProperties {
        if (fulfillmentFailureThrottle == null
                || fulfillmentFailureThrottle.isZero()
                || fulfillmentFailureThrottle.isNegative()) {
            throw new IllegalArgumentException(
                    "결제 상품 지급 실패 로그 throttle 주기는 0보다 커야 합니다."
            );
        }
    }
}
