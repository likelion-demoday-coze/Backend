package demoday.backend.store.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements BaseErrorCode {
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "STORE_403_1", "탈퇴한 회원은 상점을 이용할 수 없습니다."),
    INVALID_PURCHASE(HttpStatus.BAD_REQUEST, "STORE_400_1", "구매 수량과 요청 식별 값이 올바르지 않습니다."),
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "STORE_404_1", "상점 아이템을 찾을 수 없습니다."),
    ITEM_UNAVAILABLE(HttpStatus.CONFLICT, "STORE_409_1", "판매 중지된 아이템입니다."),
    PURCHASE_CONFLICT(HttpStatus.CONFLICT, "STORE_409_2", "동일 구매 요청 식별 값이 다른 구매에 사용되었습니다."),
    QUANTITY_OVERFLOW(HttpStatus.CONFLICT, "STORE_409_3", "아이템 최대 보유 수량을 초과합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
