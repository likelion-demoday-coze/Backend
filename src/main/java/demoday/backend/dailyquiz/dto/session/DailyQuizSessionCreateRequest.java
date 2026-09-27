package demoday.backend.dailyquiz.dto.session;

import demoday.backend.quiz.code.QuizCategory;
import jakarta.validation.constraints.NotNull;

public record DailyQuizSessionCreateRequest(

        @NotNull
        QuizCategory category
) {
}
