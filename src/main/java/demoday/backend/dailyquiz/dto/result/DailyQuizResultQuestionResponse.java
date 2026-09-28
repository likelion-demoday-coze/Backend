package demoday.backend.dailyquiz.dto.result;

import demoday.backend.dailyquiz.domain.DailyQuizAttempt;
import demoday.backend.quiz.domain.QuizOption;

import java.math.BigDecimal;

public record DailyQuizResultQuestionResponse(
        Long sessionQuestionId,
        Long questionId,
        String content,
        Long selectedOptionId,
        Long correctOptionId,
        String correctOptionContent,
        Boolean correct,
        String explanation,
        Boolean retryAnswered,
        Boolean retryCorrect,
        Integer stockIncreasePercent,
        BigDecimal stockAfter
) {

    public static DailyQuizResultQuestionResponse of(
            DailyQuizAttempt originalAttempt,
            QuizOption correctOption,
            DailyQuizAttempt retryAttempt,
            BigDecimal stockAfter
    ) {
        return new DailyQuizResultQuestionResponse(
                originalAttempt.getSessionQuestion().getSessionQuestionId(),
                originalAttempt.getSessionQuestion().getQuestion().getQuestionId(),
                originalAttempt.getSessionQuestion().getQuestion().getContent(),
                originalAttempt.getSelectedOption().getOptionId(),
                correctOption.getOptionId(),
                correctOption.getContent(),
                originalAttempt.getCorrect(),
                originalAttempt.getSessionQuestion().getQuestion().getExplanation(),
                retryAttempt != null,
                retryAttempt != null
                        ? retryAttempt.getCorrect()
                        : null,
                originalAttempt.getStockIncreasePercent(),
                stockAfter
        );
    }
}
