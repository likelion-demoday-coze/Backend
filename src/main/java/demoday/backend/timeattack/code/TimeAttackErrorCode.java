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
    ),
    DAILY_ATTEMPT_LIMIT_EXCEEDED(
            HttpStatus.CONFLICT,
            "TIME_ATTACK_409_4",
            "오늘의 타임어택 참여 가능 횟수를 모두 사용했습니다."
    ),
    SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TIME_ATTACK_404_1",
            "타임어택 세션을 찾을 수 없습니다."
    ),

    NO_AVAILABLE_QUESTION(
            HttpStatus.NOT_FOUND,
            "TIME_ATTACK_404_2",
            "출제할 수 있는 타임어택 문제가 없습니다."
    ),
    QUESTION_NOT_CURRENT(
            HttpStatus.CONFLICT,
            "TIME_ATTACK_409_5",
            "현재 출제된 타임어택 문제가 아닙니다."
    ),

    ANSWER_ALREADY_SUBMITTED(
            HttpStatus.CONFLICT,
            "TIME_ATTACK_409_6",
            "이미 답안을 제출한 문제입니다."
    ),

    OPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TIME_ATTACK_404_3",
            "선택지를 찾을 수 없습니다."
    ),

    CORRECT_OPTION_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "TIME_ATTACK_500_1",
            "문제의 정답 선택지를 찾을 수 없습니다."
    ),
    RESULT_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "TIME_ATTACK_409_7",
            "정상 완료된 타임어택 세션만 결과를 조회할 수 있습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
