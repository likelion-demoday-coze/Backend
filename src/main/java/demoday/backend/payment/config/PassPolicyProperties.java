package demoday.backend.payment.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.pass")
public record PassPolicyProperties(
        @NotNull Duration duration
) {

    public PassPolicyProperties {
        if (duration != null
                && (duration.isZero()
                || duration.isNegative()
                || duration.toMinutesPart() != 0
                || duration.toSecondsPart() != 0
                || duration.toHours() > Integer.MAX_VALUE)) {
            throw new IllegalArgumentException(
                    "패스 기간은 1시간 이상의 시간 단위여야 합니다."
            );
        }
    }

    public int durationHours() {
        return Math.toIntExact(duration.toHours());
    }
}
