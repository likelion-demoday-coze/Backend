package demoday.backend.timeattack.dto;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.timeattack.domain.TimeAttackAnswer;
import demoday.backend.timeattack.domain.TimeAttackSession;

public record TimeAttackAnswerResponse(
        Long questionId,
        Long selectedOptionId,
        Long correctOptionId,
        Boolean correct,
        Integer correctCount,
        Integer consecutiveCorrectCount
) {

    public static TimeAttackAnswerResponse of(
            TimeAttackSession session,
            TimeAttackAnswer answer,
            QuizOption correctOption,
            int consecutiveCorrectCount
    ) {
        return new TimeAttackAnswerResponse(
                answer.getQuestion().getQuestionId(),
                answer.getSelectedOption().getOptionId(),
                correctOption.getOptionId(),
                answer.getCorrect(),
                session.getCorrectCount(),
                consecutiveCorrectCount
        );
    }
}
