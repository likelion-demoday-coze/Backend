package demoday.backend.global.logging;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class LogThrottle {

    private static final int MAX_KEY_COUNT = 1_000;

    private final Clock clock;

    private final Map<String, Instant> lastLoggedAt =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<String, Instant> eldest
                ) {
                    return size() > MAX_KEY_COUNT;
                }
            };

    public LogThrottle(Clock clock) {
        this.clock = clock;
    }

    public synchronized boolean shouldLog(
            String key,
            Duration interval
    ) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "로그 throttle key는 필수입니다."
            );
        }

        if (interval == null || interval.isNegative()
                || interval.isZero()) {
            throw new IllegalArgumentException(
                    "로그 throttle 주기는 0보다 커야 합니다."
            );
        }

        Instant now = clock.instant();
        Instant previous = lastLoggedAt.get(key);

        if (previous != null
                && now.isBefore(previous.plus(interval))) {
            return false;
        }

        lastLoggedAt.put(key, now);
        return true;
    }

    public synchronized void reset(String key) {
        lastLoggedAt.remove(key);
    }
}
