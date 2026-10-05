package demoday.backend.streak.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StreakErrorCode implements BaseErrorCode {
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "STREAK_403_1", "탈퇴한 회원은 연속 학습 기능을 이용할 수 없습니다."),
    RECOVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "STREAK_404_1", "복구할 하락 기록을 찾을 수 없습니다."),
    NO_RECOVERY_ITEM(HttpStatus.CONFLICT, "STREAK_409_1", "보유한 연속 학습 복구권이 없습니다."),
    DAILY_RECOVERY_LIMIT(HttpStatus.CONFLICT, "STREAK_409_2", "오늘 이미 복구권을 사용했습니다."),
    STALE_RECOVERY(HttpStatus.CONFLICT, "STREAK_409_3", "이미 새 학습을 시작한 이전 하락은 복구할 수 없습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
