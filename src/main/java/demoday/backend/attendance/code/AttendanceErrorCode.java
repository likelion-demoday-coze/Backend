package demoday.backend.attendance.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AttendanceErrorCode implements BaseErrorCode {
    INACTIVE_MEMBER(HttpStatus.FORBIDDEN, "ATTENDANCE_403_1", "탈퇴한 회원은 출석 보상을 이용할 수 없습니다."),
    ACTIVE_PASS(HttpStatus.CONFLICT, "ATTENDANCE_409_1", "패스 이용 중에는 출석 보상을 받을 수 없습니다."),
    INVALID_REWARD_DATE(HttpStatus.CONFLICT, "ATTENDANCE_409_2", "마지막 수령일이 현재 날짜보다 늦어 출석 보상을 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
