package demoday.backend.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 변경 요청")
public record NicknameUpdateRequest(

        @Schema(
                description = "변경할 닉네임",
                example = "코지파이팅"
        )
        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(
                min = 2,
                max = 8,
                message = "닉네임은 2자 이상 8자 이하여야 합니다."
        )
        @Pattern(
                regexp = "^[가-힣A-Za-z0-9]+$",
                message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
        )
        String nickname
) {
}
