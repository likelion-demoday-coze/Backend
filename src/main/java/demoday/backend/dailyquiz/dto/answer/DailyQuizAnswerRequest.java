package demoday.backend.dailyquiz.dto.answer;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import jakarta.validation.constraints.NotNull;

public record DailyQuizAnswerRequest(
        @NotNull Long selectedOptionId,
        @NotNull DailyQuizAttemptType attemptType
) {
}
