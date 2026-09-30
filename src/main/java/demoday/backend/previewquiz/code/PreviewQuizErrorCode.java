package demoday.backend.previewquiz.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PreviewQuizErrorCode implements BaseErrorCode {

    INSUFFICIENT_QUESTIONS(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PREVIEW_QUIZ_500_1",
            "등록된 맛보기 퀴즈 문제가 부족합니다."
    ),

    QUESTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PREVIEW_QUIZ_404_1",
            "맛보기 퀴즈 문제를 찾을 수 없습니다."
    ),

    OPTION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PREVIEW_QUIZ_404_2",
            "해당 문제의 선택지를 찾을 수 없습니다."
    ),

    CORRECT_OPTION_NOT_FOUND(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PREVIEW_QUIZ_500_2",
            "문제의 정답 선택지가 구성되지 않았습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
