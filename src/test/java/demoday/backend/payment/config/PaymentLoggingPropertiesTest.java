package demoday.backend.payment.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentLoggingPropertiesTest {

    @Test
    void acceptsPositiveThrottleDuration() {
        assertThatCode(() ->
                new PaymentLoggingProperties(Duration.ofMinutes(30))
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroThrottleDuration() {
        assertThatThrownBy(() ->
                new PaymentLoggingProperties(Duration.ZERO)
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeThrottleDuration() {
        assertThatThrownBy(() ->
                new PaymentLoggingProperties(Duration.ofSeconds(-1))
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullThrottleDuration() {
        assertThatThrownBy(() ->
                new PaymentLoggingProperties(null)
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
