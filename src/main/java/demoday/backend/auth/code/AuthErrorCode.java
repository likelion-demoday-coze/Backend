package demoday.backend.auth.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    INVALID_REDIRECT_URL(
            HttpStatus.BAD_REQUEST,
            "AUTH_400_1",
            "허용되지 않은 프론트 리다이렉트 주소입니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
