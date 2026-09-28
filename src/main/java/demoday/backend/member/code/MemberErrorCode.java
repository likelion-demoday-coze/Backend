package demoday.backend.member.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    INVALID_FISH_AMOUNT(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_1",
            "변경할 생선 수량은 1 이상이어야 합니다."
    ),
    INVALID_STOCK_INCREASE_PERCENT(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_2",
            "주가 상승률은 1 이상 10 이하이어야 합니다."
    ),
    INSUFFICIENT_FISH(
            HttpStatus.CONFLICT,
            "MEMBER_409_1",
            "생선 잔액이 부족합니다."
    ),
    FISH_BALANCE_OVERFLOW(
            HttpStatus.CONFLICT,
            "MEMBER_409_2",
            "보유 가능한 생선 수량을 초과했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
