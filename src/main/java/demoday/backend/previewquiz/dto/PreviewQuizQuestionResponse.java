package demoday.backend.previewquiz.dto;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;

import java.util.List;

public record PreviewQuizQuestionResponse(
        Long questionId,
        String questionType,
        String content,
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
