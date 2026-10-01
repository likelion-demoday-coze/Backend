package demoday.backend.quiz.dto;

import demoday.backend.quiz.domain.QuizOption;

public record QuizOptionResponse(
        Long optionId,
        Integer optionNumber,
        String content
) {

    public static QuizOptionResponse from(
            QuizOption quizOption
    ) {
        return new QuizOptionResponse(
                quizOption.getOptionId(),
                quizOption.getOptionNumber(),
                quizOption.getContent()
        );
    }
}
