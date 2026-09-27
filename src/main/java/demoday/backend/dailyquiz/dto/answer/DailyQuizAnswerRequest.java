package demoday.backend.dailyquiz.dto.answer;

import jakarta.validation.constraints.NotNull;

public record DailyQuizAnswerRequest(
        @NotNull Long selectedOptionId
) {
}
