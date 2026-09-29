package demoday.backend.stock.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StockErrorCode implements BaseErrorCode {

    INVALID_CHANGE(HttpStatus.BAD_REQUEST, "STOCK_400_3", "주가 변경 사유 또는 참조 ID가 올바르지 않습니다."),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "STOCK_400_4", "작업 식별 키는 1~100자의 영문, 숫자, 콜론, 밑줄, 하이픈이어야 합니다."),
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "STOCK_409_1", "이미 다른 주가 변경에 사용된 작업 식별 키입니다."),
    STALE_STOCK(HttpStatus.CONFLICT, "STOCK_409_2", "변경 전 주가가 현재 주가와 다릅니다. 최신 주가를 기준으로 다시 계산해야 합니다."),

    INVALID_HISTORY_PERIOD(HttpStatus.BAD_REQUEST, "STOCK_400_2", "시작일과 종료일을 함께 입력하고, 시작일이 종료일 이하인 최대 366일의 기간을 지정해야 합니다."),

    INVALID_PAGE(HttpStatus.BAD_REQUEST, "STOCK_400_1", "페이지는 0 이상, 페이지 크기는 1~100이어야 합니다."),
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "STOCK_403_1", "탈퇴한 회원은 주가 기능을 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
