package demoday.backend.dailyquiz.dto;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.quiz.code.QuizCategory;

import java.time.LocalDateTime;

public record DailyQuizSessionCreateResponse(
        Long sessionId,
        QuizCategory category,
        DailyQuizSessionStatus status,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        Boolean passApplied
) {

    public static DailyQuizSessionCreateResponse from(
            DailyQuizSession session
    ) {
        return new DailyQuizSessionCreateResponse(
                session.getDailyQuizSessionId(),
                session.getCategory(),
                session.getStatus(),
                session.getStartedAt(),
                session.getExpiresAt(),
                session.getPassApplied()
        );
    }
}
