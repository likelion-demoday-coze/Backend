package demoday.backend.previewquiz.dto;

import demoday.backend.quiz.domain.QuizOption;

public record PreviewQuizOptionResponse(
        Long optionId,
        Integer optionNumber,
        String content
) {

    public static PreviewQuizOptionResponse from(QuizOption quizOption) {
        return new PreviewQuizOptionResponse(
                quizOption.getOptionId(),
                quizOption.getOptionNumber(),
                quizOption.getContent()
        );
    }
}
