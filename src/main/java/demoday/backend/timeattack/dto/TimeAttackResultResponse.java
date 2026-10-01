package demoday.backend.timeattack.dto;

import demoday.backend.timeattack.code.TimeAttackStatus;
import demoday.backend.timeattack.domain.TimeAttackSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TimeAttackResultResponse(
        Long sessionId,
        LocalDate attemptDate,
        TimeAttackStatus status,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        Integer totalAnsweredCount,
        Integer correctCount,
        Integer incorrectCount,
        Integer maxConsecutiveCorrectCount,
        List<TimeAttackResultQuestionResponse> questions
) {

    public static TimeAttackResultResponse of(
            TimeAttackSession session,
            int maxConsecutiveCorrectCount,
            List<TimeAttackResultQuestionResponse> questions
    ) {
        int totalAnsweredCount = questions.size();
        int correctCount = session.getCorrectCount();

        return new TimeAttackResultResponse(
                session.getTimeAttackSessionId(),
                session.getAttemptDate(),
                session.getStatus(),
                session.getStartedAt(),
                session.getEndsAt(),
                totalAnsweredCount,
                correctCount,
                totalAnsweredCount - correctCount,
                maxConsecutiveCorrectCount,
                questions
        );
    }
}
