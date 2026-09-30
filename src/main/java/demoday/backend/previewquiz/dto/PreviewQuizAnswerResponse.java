package demoday.backend.previewquiz.dto;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;

public record PreviewQuizAnswerResponse(
        Long questionId,
        Long selectedOptionId,
        Long correctOptionId,
        String correctOptionContent,
        Boolean correct,
        String explanation
) {

    public static PreviewQuizAnswerResponse of(
            QuizQuestion question,
            QuizOption selectedOption,
            QuizOption correctOption
    ) {
        return new PreviewQuizAnswerResponse(
                question.getQuestionId(),
                selectedOption.getOptionId(),
                correctOption.getOptionId(),
                correctOption.getContent(),
                selectedOption.getCorrect(),
                question.getExplanation()
        );
    }
}
