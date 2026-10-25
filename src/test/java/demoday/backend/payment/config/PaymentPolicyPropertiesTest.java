package demoday.backend.payment.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentPolicyPropertiesTest {

    @Test
    @DisplayName("11월 14일 전체는 판매하고 11월 15일 0시부터 차단한다")
    void appliesExclusiveSalesEndBoundary() {
        ServicePeriodProperties properties =
                new ServicePeriodProperties(
                        OffsetDateTime.parse(
                                "2026-11-15T00:00:00+09:00"
                        ),
                        OffsetDateTime.parse(
                                "2026-11-21T23:59:59+09:00"
                        )
                );

        assertThat(properties.isSalesOpenAt(
                LocalDateTime.parse("2026-11-14T23:59:59.999999")
        )).isTrue();
        assertThat(properties.isSalesOpenAt(
                LocalDateTime.parse("2026-11-15T00:00:00")
        )).isFalse();
    }

    @Test
    @DisplayName("패스 기간은 환경 설정의 168시간을 사용한다")
    void convertsPassDurationToHours() {
        PassPolicyProperties properties =
                new PassPolicyProperties(Duration.ofHours(168));

        assertThat(properties.durationHours()).isEqualTo(168);
    }
}
