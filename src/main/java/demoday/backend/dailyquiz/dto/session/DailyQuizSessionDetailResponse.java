package demoday.backend.dailyquiz.dto.session;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.dto.question.DailyQuizQuestionResponse;
import demoday.backend.quiz.code.QuizCategory;

import java.time.LocalDateTime;
import java.util.List;

public record DailyQuizSessionDetailResponse(
        Long sessionId,
        QuizCategory category,
        DailyQuizSessionStatus status,
        long answeredCount,
        int totalQuestionCount,
        LocalDateTime startedAt,
        LocalDateTime expiresAt,
        Boolean passApplied,
        List<DailyQuizQuestionResponse> questions
) {

    public static DailyQuizSessionDetailResponse of(
            DailyQuizSession session,
            long answeredCount,
            List<DailyQuizQuestionResponse> questions
    ) {
        return new DailyQuizSessionDetailResponse(
                session.getDailyQuizSessionId(),
                session.getCategory(),
                session.getStatus(),
                answeredCount,
                questions.size(),
                session.getStartedAt(),
                session.getExpiresAt(),
                session.getPassApplied(),
                questions
        );
    }
}
