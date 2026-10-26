package demoday.backend.global.logging;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LogThrottleTest {

    @Test
    void sameKeyIsLoggedOnlyOnceWithinInterval() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-26T00:00:00Z"),
                ZoneOffset.UTC
        );

        LogThrottle throttle = new LogThrottle(clock);

        assertThat(
                throttle.shouldLog(
                        "payment:1",
                        Duration.ofMinutes(30)
                )
        ).isTrue();

        assertThat(
                throttle.shouldLog(
                        "payment:1",
                        Duration.ofMinutes(30)
                )
        ).isFalse();

        assertThat(
                throttle.shouldLog(
                        "payment:2",
                        Duration.ofMinutes(30)
                )
        ).isTrue();
    }

    @Test
    void resetAllowsSameKeyToBeLoggedAgain() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-10-26T00:00:00Z"),
                ZoneOffset.UTC
        );

        LogThrottle throttle = new LogThrottle(clock);

        throttle.shouldLog(
                "payment:1",
                Duration.ofMinutes(30)
        );

        throttle.reset("payment:1");

        assertThat(
                throttle.shouldLog(
                        "payment:1",
                        Duration.ofMinutes(30)
                )
        ).isTrue();
    }
}
