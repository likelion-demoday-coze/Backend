package demoday.backend.payment.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "app.service")
public record ServicePeriodProperties(
        @NotNull OffsetDateTime salesEndAt,
        @NotNull OffsetDateTime serviceEndAt
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public ServicePeriodProperties {
        if (salesEndAt != null && serviceEndAt != null
                && serviceEndAt.toInstant().isBefore(
                salesEndAt.toInstant()
        )) {
            throw new IllegalArgumentException(
                    "서비스 종료 시각은 판매 종료 시각보다 빠를 수 없습니다."
            );
        }
    }

    public boolean isSalesOpenAt(LocalDateTime kstDateTime) {
        return kstDateTime.atZone(KST).toInstant()
                .isBefore(salesEndAt.toInstant());
    }
}
