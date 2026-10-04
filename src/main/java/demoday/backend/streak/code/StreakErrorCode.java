package demoday.backend.streak.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StreakErrorCode implements BaseErrorCode {
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "STREAK_403_1", "탈퇴한 회원은 연속 학습 기능을 이용할 수 없습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
