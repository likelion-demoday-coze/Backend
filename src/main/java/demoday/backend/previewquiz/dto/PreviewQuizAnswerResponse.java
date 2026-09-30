package demoday.backend.previewquiz.dto;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "맛보기 퀴즈 답안 제출 결과")
public record PreviewQuizAnswerResponse(

        @Schema(description = "문제 ID", example = "1")
        Long questionId,

        @Schema(description = "사용자가 선택한 선택지 ID", example = "2")
        Long selectedOptionId,

        @Schema(description = "정답 선택지 ID", example = "2")
        Long correctOptionId,

        @Schema(description = "정답 선택지 내용", example = "영업이익")
        String correctOptionContent,

        @Schema(description = "정답 여부", example = "true")
        Boolean correct,

        @Schema(
                description = "문제 해설",
                example = "영업이익은 매출총이익에서 판매비와 관리비를 차감한 금액입니다."
        )
        String explanation
) {

    public static PreviewQuizAnswerResponse of(
            QuizQuestion question,
            QuizOption selectedOption,
            QuizOption correctOption
    ) {
        return new PreviewQuizAnswerResponse(
                question.getQuestionId(),
                selectedOption.getOptionId(),
                correctOption.getOptionId(),
                correctOption.getContent(),
                selectedOption.getCorrect(),
                question.getExplanation()
        );
    }
}
