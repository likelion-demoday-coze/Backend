package demoday.backend.fish.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FishErrorCode implements BaseErrorCode {

    INVALID_TRANSACTION_TYPE(HttpStatus.BAD_REQUEST, "FISH_400_1", "지급 또는 차감 사유가 올바르지 않습니다."),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "FISH_400_2", "작업 식별 키는 공백 없이 1~100자의 영문, 숫자, 콜론, 밑줄, 하이픈으로 입력해야 합니다."),
    INVALID_PAGE(HttpStatus.BAD_REQUEST, "FISH_400_3", "페이지는 0 이상, 페이지 크기는 1~100이어야 합니다."),
    INVALID_REFERENCE_ID(HttpStatus.BAD_REQUEST, "FISH_400_4", "참조 ID는 1 이상이어야 합니다."),
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "FISH_403_1", "탈퇴한 회원은 생선 기능을 이용할 수 없습니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "FISH_409_1", "이미 다른 거래에 사용된 작업 식별 키입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
