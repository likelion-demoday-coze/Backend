package demoday.backend.dailyquiz.dto.answer;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "데일리 퀴즈 답안 제출 결과",
        oneOf = {
                DailyQuizAnswerResponse.class,
                DailyQuizRetryAnswerResponse.class
        }
)
public interface DailyQuizAnswerResult {
}
