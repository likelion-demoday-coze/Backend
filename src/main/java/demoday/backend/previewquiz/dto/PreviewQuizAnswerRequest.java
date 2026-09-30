package demoday.backend.previewquiz.dto;

import jakarta.validation.constraints.NotNull;

public record PreviewQuizAnswerRequest(

        @NotNull(message = "선택지 ID는 필수입니다.")
        Long selectedOptionId
) {
}
