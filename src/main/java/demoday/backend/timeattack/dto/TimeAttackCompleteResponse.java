package demoday.backend.timeattack.dto;

import demoday.backend.timeattack.code.TimeAttackStatus;
import demoday.backend.timeattack.domain.TimeAttackSession;

public record TimeAttackCompleteResponse(
        Long sessionId,
        TimeAttackStatus status,
        Integer totalAnsweredCount,
        Integer correctCount,
        Integer incorrectCount,
        Integer maxConsecutiveCorrectCount
) {

    public static TimeAttackCompleteResponse of(
            TimeAttackSession session,
            int totalAnsweredCount,
            int maxConsecutiveCorrectCount
    ) {
        int correctCount = session.getCorrectCount();

        return new TimeAttackCompleteResponse(
                session.getTimeAttackSessionId(),
                session.getStatus(),
                totalAnsweredCount,
                correctCount,
                totalAnsweredCount - correctCount,
                maxConsecutiveCorrectCount
        );
    }
}
