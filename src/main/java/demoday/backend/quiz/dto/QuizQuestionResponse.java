package demoday.backend.quiz.dto;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;

import java.util.List;

public record QuizQuestionResponse(
        Long questionId,
        String questionType,
        String content,
        List<QuizOptionResponse> options
) {

    public static QuizQuestionResponse of(
            QuizQuestion quizQuestion,
            List<QuizOption> options
    ) {
        return new QuizQuestionResponse(
                quizQuestion.getQuestionId(),
                quizQuestion.getQuestionType(),
                quizQuestion.getContent(),
                options.stream()
                        .map(QuizOptionResponse::from)
                        .toList()
        );
    }
}
