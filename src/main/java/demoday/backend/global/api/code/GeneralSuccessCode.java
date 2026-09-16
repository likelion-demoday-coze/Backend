package demoday.backend.global.api.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeneralSuccessCode implements BaseSuccessCode {

    OK(HttpStatus.OK, "COMMON_200", "요청을 성공적으로 처리했습니다."),
    CREATED(HttpStatus.CREATED, "COMMON_201", "리소스를 성공적으로 생성했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
