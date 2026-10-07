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

    INVALID_STOCK_VALUE(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_3",
            "주가는 0 이상, 정수부 28자리 이하, 소수점 둘째 자리까지의 값이어야 합니다."
    ),

    INVALID_NICKNAME(
            HttpStatus.BAD_REQUEST,
            "MEMBER_400_4",
            "닉네임은 한글, 영문, 숫자로 구성된 2~8자여야 합니다."
    ),

    INACTIVE_MEMBER(
            HttpStatus.FORBIDDEN,
            "MEMBER_403_1",
            "탈퇴한 회원은 이용할 수 없습니다."
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
    ),

    NICKNAME_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "MEMBER_409_3",
            "이미 사용 중인 닉네임입니다."
    ),

    ALREADY_WITHDRAWN(
            HttpStatus.CONFLICT,
            "MEMBER_409_4",
            "이미 탈퇴한 회원입니다."
    ),
    WITHDRAWAL_BLOCKED_BY_PENDING_PAYMENT(
            HttpStatus.CONFLICT,
            "MEMBER_409_5",
            "처리 중인 결제가 있어 회원 탈퇴를 진행할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
