package demoday.backend.dailyquiz.dto.category;

import demoday.backend.quiz.code.QuizCategory;

public record DailyQuizCategoryResponse(
        QuizCategory category
) {

    public static DailyQuizCategoryResponse from(QuizCategory category) {
        return new DailyQuizCategoryResponse(category);
    }
}
