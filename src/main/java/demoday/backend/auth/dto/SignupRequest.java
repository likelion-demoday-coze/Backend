package demoday.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(max = 10, message = "닉네임은 10자 이하여야 합니다.")
        String nickname
) {
}
