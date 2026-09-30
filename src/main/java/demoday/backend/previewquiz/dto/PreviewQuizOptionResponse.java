// PreviewQuizOptionResponse.java

package demoday.backend.previewquiz.dto;

import demoday.backend.quiz.domain.QuizOption;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "맛보기 퀴즈 선택지 응답")
public record PreviewQuizOptionResponse(

        @Schema(description = "선택지 ID", example = "1")
        Long optionId,

        @Schema(description = "선택지 번호", example = "1")
        Integer optionNumber,

        @Schema(description = "선택지 내용", example = "매출총이익")
        String content
) {

    public static PreviewQuizOptionResponse from(QuizOption quizOption) {
        return new PreviewQuizOptionResponse(
                quizOption.getOptionId(),
                quizOption.getOptionNumber(),
                quizOption.getContent()
        );
    }
}
