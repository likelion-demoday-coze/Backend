package demoday.backend.dailyquiz.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DailyQuizErrorCode implements BaseErrorCode {

    ACTIVE_SESSION_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_1",
            "이미 진행 중인 데일리 퀴즈가 있습니다."
    ),
    SESSION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "DAILY_QUIZ_404_1",
            "데일리 퀴즈 세션을 찾을 수 없습니다."
    ),
    SESSION_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "DAILY_QUIZ_403_1",
            "해당 데일리 퀴즈 세션에 접근할 수 없습니다."
    ),
    SESSION_EXPIRED(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_2",
            "만료된 데일리 퀴즈 세션입니다."
    ),
    SESSION_ALREADY_COMPLETED(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_3",
            "이미 완료된 데일리 퀴즈 세션입니다."
    ),
    INSUFFICIENT_FISH(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_4",
            "보유한 생선이 부족합니다."
    ),
    INSUFFICIENT_QUESTIONS(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_5",
            "출제 가능한 문제가 부족합니다."
    ),
    SESSION_QUESTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "DAILY_QUIZ_404_2",
            "세션에 포함된 문제를 찾을 수 없습니다."
    ),
    OPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "DAILY_QUIZ_404_3",
            "선택지를 찾을 수 없습니다."
    ),
    INVALID_OPTION(
            HttpStatus.BAD_REQUEST,
            "DAILY_QUIZ_400_1",
            "해당 문제에 속하지 않는 선택지입니다."
    ),
    ANSWER_ALREADY_SUBMITTED(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_6",
            "이미 답안을 제출한 문제입니다."
    ),
    RETRY_NOT_ALLOWED(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_7",
            "재풀이할 수 없는 문제입니다."
    ),
    INVALID_SESSION_STATE(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_8",
            "현재 상태에서는 요청한 세션 상태 변경을 할 수 없습니다."
    ),
    SESSION_NOT_EXPIRED(
            HttpStatus.CONFLICT,
            "DAILY_QUIZ_409_9",
            "아직 만료되지 않은 데일리 퀴즈 세션입니다."
    ),
    INVALID_SESSION_QUESTION_COUNT(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "DAILY_QUIZ_500_1",
            "세션 문제 구성이 올바르지 않습니다."
    ),
    CORRECT_OPTION_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "DAILY_QUIZ_500_2",
            "문제의 정답 선택지가 구성되지 않았습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
