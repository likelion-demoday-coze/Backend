package demoday.backend.timeattack.dto;

import demoday.backend.timeattack.code.TimeAttackStatus;
import demoday.backend.timeattack.domain.TimeAttackSession;

import java.time.LocalDateTime;

public record TimeAttackSessionCreateResponse(
        Long sessionId,
        TimeAttackStatus status,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        Boolean passApplied
) {

    public static TimeAttackSessionCreateResponse from(
            TimeAttackSession session
    ) {
        return new TimeAttackSessionCreateResponse(
                session.getTimeAttackSessionId(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndsAt(),
                session.getPassApplied()
        );
    }
}
