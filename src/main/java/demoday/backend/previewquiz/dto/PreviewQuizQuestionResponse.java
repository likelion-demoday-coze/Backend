package demoday.backend.previewquiz.dto;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "맛보기 퀴즈 문제 응답")
public record PreviewQuizQuestionResponse(

        @Schema(description = "문제 ID", example = "1")
        Long questionId,

        @Schema(description = "문제 유형", example = "MULTIPLE_CHOICE")
        String questionType,

        @Schema(
                description = "문제 내용",
                example = "기업의 주된 영업활동에서 발생한 수익에서 매출원가를 뺀 이익은?"
        )
        String content,

        @Schema(description = "선택지 목록")
        List<PreviewQuizOptionResponse> options
) {

    public static PreviewQuizQuestionResponse of(
            QuizQuestion question,
            List<QuizOption> options
    ) {
        return new PreviewQuizQuestionResponse(
                question.getQuestionId(),
                question.getQuestionType(),
                question.getContent(),
                options.stream()
                        .map(PreviewQuizOptionResponse::from)
                        .toList()
        );
    }
}
