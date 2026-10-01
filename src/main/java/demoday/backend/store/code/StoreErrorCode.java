package demoday.backend.store.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements BaseErrorCode {
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "STORE_403_1", "탈퇴한 회원은 상점을 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
