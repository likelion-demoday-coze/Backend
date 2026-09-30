package demoday.backend.timeattack.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TimeAttackErrorCode implements BaseErrorCode {

    SESSION_NOT_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "TIME_ATTACK_409_1",
            "진행 중인 타임어택 세션이 아닙니다."
    ),

    SESSION_EXPIRED(
            HttpStatus.CONFLICT,
            "TIME_ATTACK_409_2",
            "타임어택 제한 시간이 종료되었습니다."
    ),

    TOO_EARLY_TO_COMPLETE(
            HttpStatus.CONFLICT,
            "TIME_ATTACK_409_3",
            "제한 시간이 지나기 전에는 타임어택을 종료할 수 없습니다."
    ),

    OPTION_NOT_BELONGS_TO_QUESTION(
            HttpStatus.BAD_REQUEST,
            "TIME_ATTACK_400_1",
            "선택지가 해당 타임어택 문제에 속하지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
