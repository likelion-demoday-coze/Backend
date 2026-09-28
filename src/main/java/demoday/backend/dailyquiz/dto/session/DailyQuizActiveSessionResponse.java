package demoday.backend.dailyquiz.dto.session;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.quiz.code.QuizCategory;

import java.time.LocalDateTime;

public record DailyQuizActiveSessionResponse(
        Long sessionId,
        QuizCategory category,
        DailyQuizSessionStatus status,
        long answeredCount,
        int totalQuestionCount,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        Boolean passApplied
) {

    public static DailyQuizActiveSessionResponse of(
            DailyQuizSession session,
            long answeredCount,
            int totalQuestionCount
    ) {
        return new DailyQuizActiveSessionResponse(
                session.getDailyQuizSessionId(),
                session.getCategory(),
                session.getStatus(),
                answeredCount,
                totalQuestionCount,
                session.getStartedAt(),
                session.getExpiresAt(),
                session.getPassApplied()
        );
    }
}
