package demoday.backend.auth.service;

import demoday.backend.auth.code.AuthErrorCode;
import demoday.backend.global.exception.ProjectException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SignupAvailabilityServiceTest {

    private static final OffsetDateTime OPEN_AT =
            OffsetDateTime.parse("2026-10-31T00:00:00+09:00");

    @Test
    void 오픈_시각_전에는_회원가입이_닫혀_있다() {
        SignupAvailabilityService service = serviceAt(
                "2026-10-30T14:59:59Z"
        );

        assertThat(service.isOpen()).isFalse();
        assertThatThrownBy(service::validateOpen)
                .isInstanceOf(ProjectException.class)
                .satisfies(exception ->
                        assertThat(((ProjectException) exception).getErrorCode())
                                .isEqualTo(AuthErrorCode.SIGNUP_NOT_OPEN)
                );
    }

    @Test
    void 오픈_시각부터_회원가입이_열린다() {
        SignupAvailabilityService service = serviceAt(
                "2026-10-30T15:00:00Z"
        );

        assertThat(service.isOpen()).isTrue();
    }

    private SignupAvailabilityService serviceAt(String instant) {
        Clock clock = Clock.fixed(
                Instant.parse(instant),
                ZoneOffset.UTC
        );
        return new SignupAvailabilityService(clock, OPEN_AT);
    }
}
