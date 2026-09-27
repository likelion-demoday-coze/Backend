package demoday.backend.dailyquiz.dto.answer;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizAttempt;

import java.math.BigDecimal;

public record DailyQuizAnswerResponse(
        Long sessionQuestionId,
        Long selectedOptionId,
        Boolean correct,
        Long correctOptionId,
        String explanation,
        Integer stockIncreasePercent,
        BigDecimal currentStock,
        long answeredCount,
        int totalQuestionCount,
        DailyQuizSessionStatus sessionStatus
) {

    public static DailyQuizAnswerResponse of(
            DailyQuizAttempt attempt,
            Long correctOptionId,
            String explanation,
            BigDecimal currentStock,
            long answeredCount,
            int totalQuestionCount,
            DailyQuizSessionStatus sessionStatus
    ) {
        return new DailyQuizAnswerResponse(
                attempt.getSessionQuestion().getSessionQuestionId(),
                attempt.getSelectedOption().getOptionId(),
                attempt.getCorrect(),
                correctOptionId,
                explanation,
                attempt.getStockIncreasePercent(),
                currentStock,
                answeredCount,
                totalQuestionCount,
                sessionStatus
        );
    }
}
