package demoday.backend.dailyquiz.dto.question;

import demoday.backend.quiz.domain.QuizOption;

public record DailyQuizOptionResponse(
        Long optionId,
        Integer optionNumber,
        String content
) {

    public static DailyQuizOptionResponse from(
            QuizOption option
    ) {
        return new DailyQuizOptionResponse(
                option.getOptionId(),
                option.getOptionNumber(),
                option.getContent()
        );
    }
}
