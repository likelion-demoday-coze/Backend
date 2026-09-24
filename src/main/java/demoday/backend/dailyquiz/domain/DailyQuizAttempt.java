package demoday.backend.dailyquiz.domain;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.quiz.domain.QuizOption;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Entity
@Table(
        name = "daily_quiz_attempt",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_daily_quiz_attempt_question_type",
                columnNames = {"session_question_id", "attempt_type"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DailyQuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_quiz_attempt_id")
    private Long dailyQuizAttemptId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_question_id", nullable = false)
    private DailyQuizSessionQuestion sessionQuestion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_option_id", nullable = false)
    private QuizOption selectedOption;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_type", nullable = false)
    private DailyQuizAttemptType attemptType;

    @Column(nullable = false)
    private Boolean correct;

    @Column(name = "stock_increase_percent")
    private Integer stockIncreasePercent;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    public static DailyQuizAttempt create(
            DailyQuizSessionQuestion sessionQuestion,
            QuizOption selectedOption,
            DailyQuizAttemptType attemptType,
            Integer stockIncreasePercent,
            LocalDateTime answeredAt
    ) {
        validateSelectedOption(sessionQuestion, selectedOption);

        return DailyQuizAttempt.builder()
                .sessionQuestion(sessionQuestion)
                .selectedOption(selectedOption)
                .attemptType(attemptType)
                .correct(selectedOption.getCorrect())
                .stockIncreasePercent(stockIncreasePercent)
                .answeredAt(answeredAt)
                .build();
    }

    private static void validateSelectedOption(
            DailyQuizSessionQuestion sessionQuestion,
            QuizOption selectedOption
    ) {

        Long sessionQuestionId = sessionQuestion.getQuestion().getQuestionId();
        Long selectedOptionQuestionId = selectedOption.getQuestion().getQuestionId();

        if (!Objects.equals(sessionQuestionId, selectedOptionQuestionId)) {
            throw new IllegalArgumentException(
                    "선택지가 세션 문제에 속하지 않습니다."
            );
        }
    }
}
