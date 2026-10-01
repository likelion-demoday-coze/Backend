package demoday.backend.timeattack.dto;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.timeattack.domain.TimeAttackAnswer;

public record TimeAttackResultQuestionResponse(
        Long questionId,
        String content,
        Long selectedOptionId,
        String selectedOptionContent,
        Long correctOptionId,
        String correctOptionContent,
        Boolean correct,
        String explanation
) {

    public static TimeAttackResultQuestionResponse of(
            TimeAttackAnswer answer,
            QuizOption correctOption
    ) {
        return new TimeAttackResultQuestionResponse(
                answer.getQuestion().getQuestionId(),
                answer.getQuestion().getContent(),
                answer.getSelectedOption().getOptionId(),
                answer.getSelectedOption().getContent(),
                correctOption.getOptionId(),
                correctOption.getContent(),
                answer.getCorrect(),
                answer.getQuestion().getExplanation()
        );
    }
}
