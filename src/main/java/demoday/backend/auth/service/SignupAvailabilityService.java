package demoday.backend.auth.service;

import demoday.backend.auth.code.AuthErrorCode;
import demoday.backend.global.exception.ProjectException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;

@Service
public class SignupAvailabilityService {

    private final Clock clock;
    private final OffsetDateTime openAt;

    public SignupAvailabilityService(
            Clock clock,
            @Value("${app.signup.open-at:2026-10-31T00:00:00+09:00}")
            OffsetDateTime openAt
    ) {
        this.clock = clock;
        this.openAt = openAt;
    }

    public boolean isOpen() {
        return !OffsetDateTime.now(clock).isBefore(openAt);
    }

    public void validateOpen() {
        if (!isOpen()) {
            throw new ProjectException(AuthErrorCode.SIGNUP_NOT_OPEN);
        }
    }
}
