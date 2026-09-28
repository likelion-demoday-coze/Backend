package demoday.backend.dailyquiz.dto.answer;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizAttempt;

public record DailyQuizRetryAnswerResponse(
        Long sessionQuestionId,
        Long selectedOptionId,
        Boolean correct,
        Long correctOptionId,
        String explanation,
        long retryCompletedCount,
        long retryRequiredCount,
        DailyQuizSessionStatus sessionStatus
) implements DailyQuizAnswerResult {

    public static DailyQuizRetryAnswerResponse of(
            DailyQuizAttempt attempt,
            Long correctOptionId,
            String explanation,
            long retryCompletedCount,
            long retryRequiredCount,
            DailyQuizSessionStatus sessionStatus
    ) {
        return new DailyQuizRetryAnswerResponse(
                attempt.getSessionQuestion().getSessionQuestionId(),
                attempt.getSelectedOption().getOptionId(),
                attempt.getCorrect(),
                correctOptionId,
                explanation,
                retryCompletedCount,
                retryRequiredCount,
                sessionStatus
        );
    }
}
