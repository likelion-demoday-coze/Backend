package demoday.backend.stock.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StockErrorCode implements BaseErrorCode {

    INVALID_HISTORY_PERIOD(HttpStatus.BAD_REQUEST, "STOCK_400_2", "시작일과 종료일을 함께 입력하고, 시작일이 종료일 이하인 최대 366일의 기간을 지정해야 합니다."),

    INVALID_PAGE(HttpStatus.BAD_REQUEST, "STOCK_400_1", "페이지는 0 이상, 페이지 크기는 1~100이어야 합니다."),
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "STOCK_403_1", "탈퇴한 회원은 주가 기능을 이용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
