package demoday.backend.dailyquiz.domain;

import demoday.backend.quiz.domain.QuizQuestion;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(
        name = "daily_quiz_session_question",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_quiz_session_question",
                columnNames = {"daily_quiz_session_id", "question_id"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DailyQuizSessionQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_question_id")
    private Long sessionQuestionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "daily_quiz_session_id", nullable = false)
    private DailyQuizSession dailyQuizSession;

    public static DailyQuizSessionQuestion create(
            QuizQuestion question,
            DailyQuizSession dailyQuizSession
    ) {
        return DailyQuizSessionQuestion.builder()
                .question(question)
                .dailyQuizSession(dailyQuizSession)
                .build();
    }
}
