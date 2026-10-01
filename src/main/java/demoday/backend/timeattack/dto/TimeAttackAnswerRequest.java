package demoday.backend.timeattack.dto;

import jakarta.validation.constraints.NotNull;

public record TimeAttackAnswerRequest(

        @NotNull(message = "문제 ID는 필수입니다.")
        Long questionId,

        @NotNull(message = "선택지 ID는 필수입니다.")
        Long selectedOptionId
) {
}
