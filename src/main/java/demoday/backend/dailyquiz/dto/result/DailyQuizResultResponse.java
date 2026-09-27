package demoday.backend.dailyquiz.dto.result;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.quiz.code.QuizCategory;

import java.math.BigDecimal;
import java.util.List;

public record DailyQuizResultResponse(
        Long sessionId,
        QuizCategory category,
        DailyQuizSessionStatus status,
        int correctCount,
        int incorrectCount,
        long retryCompletedCount,
        BigDecimal startStock,
        BigDecimal endStock,
        BigDecimal totalProfit,
        BigDecimal totalReturnPercent,
        List<DailyQuizResultQuestionResponse> questions
) {

    public static DailyQuizResultResponse of(
            DailyQuizSession session,
            int correctCount,
            int incorrectCount,
            long retryCompletedCount,
            BigDecimal endStock,
            BigDecimal totalProfit,
            BigDecimal totalReturnPercent,
            List<DailyQuizResultQuestionResponse> questions
    ) {
        return new DailyQuizResultResponse(
                session.getDailyQuizSessionId(),
                session.getCategory(),
                session.getStatus(),
                correctCount,
                incorrectCount,
                retryCompletedCount,
                session.getStartStock(),
                endStock,
                totalProfit,
                totalReturnPercent,
                questions
        );
    }
}
