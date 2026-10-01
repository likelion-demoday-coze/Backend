package demoday.backend.timeattack.dto.answer;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "타임어택 답안 제출 요청")
public record TimeAttackAnswerRequest(

        @Schema(
                description = "현재 출제된 문제 ID",
                example = "10",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "문제 ID는 필수입니다.")
        Long questionId,

        @Schema(
                description = "사용자가 선택한 선택지 ID",
                example = "42",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "선택지 ID는 필수입니다.")
        Long selectedOptionId
) {
}
