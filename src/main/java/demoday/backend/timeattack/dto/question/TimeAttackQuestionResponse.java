package demoday.backend.timeattack.dto.question;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.dto.QuizQuestionResponse;
import demoday.backend.timeattack.domain.TimeAttackSession;

import java.time.LocalDateTime;
import java.util.List;

public record TimeAttackQuestionResponse(
        Long sessionId,
        LocalDateTime endsAt,
        Integer correctCount,
        Integer consecutiveCorrectCount,
        QuizQuestionResponse question
) {

    public static TimeAttackQuestionResponse of(
            TimeAttackSession session,
            int consecutiveCorrectCount,
            QuizQuestion question,
            List<QuizOption> options
    ) {
        return new TimeAttackQuestionResponse(
                session.getTimeAttackSessionId(),
                session.getEndsAt(),
                session.getCorrectCount(),
                consecutiveCorrectCount,
                QuizQuestionResponse.of(
                        question,
                        options
                )
        );
    }
}
