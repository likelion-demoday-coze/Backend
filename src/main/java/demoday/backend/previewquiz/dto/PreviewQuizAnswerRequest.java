package demoday.backend.previewquiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "맛보기 퀴즈 답안 제출 요청")
public record PreviewQuizAnswerRequest(

        @Schema(
                description = "사용자가 선택한 선택지 ID",
                example = "2",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        @NotNull(message = "선택지 ID는 필수입니다.")
        Long selectedOptionId
) {
}
