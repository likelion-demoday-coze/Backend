package demoday.backend.dailyquiz.dto.question;

import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;

import java.util.List;

public record DailyQuizQuestionResponse(
        Long sessionQuestionId,
        Long questionId,
        String questionType,
        String content,
        Boolean originalAnswered,
        Boolean originalCorrect,
        Boolean retryAnswered,
        List<DailyQuizOptionResponse> options
) {

    public static DailyQuizQuestionResponse of(
            DailyQuizSessionQuestion sessionQuestion,
            boolean originalAnswered,
            Boolean originalCorrect,
            boolean retryAnswered,
            List<DailyQuizOptionResponse> options
    ) {
        return new DailyQuizQuestionResponse(
                sessionQuestion.getSessionQuestionId(),
                sessionQuestion.getQuestion().getQuestionId(),
                sessionQuestion.getQuestion().getQuestionType(),
                sessionQuestion.getQuestion().getContent(),
                originalAnswered,
                originalCorrect,
                retryAnswered,
                options
        );
    }
}
