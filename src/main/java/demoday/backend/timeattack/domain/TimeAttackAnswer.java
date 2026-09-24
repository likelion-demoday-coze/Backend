package demoday.backend.timeattack.domain;

import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "time_attack_answer",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_time_attack_answer_session_question",
                columnNames = {"time_attack_session_id", "question_id"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeAttackAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "time_attack_answer_id")
    private Long timeAttackAnswerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "time_attack_session_id", nullable = false)
    private TimeAttackSession timeAttackSession;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_option_id", nullable = false)
    private QuizOption selectedOption;

    @Column(nullable = false)
    private Boolean correct;

    @Column(name = "answered_at", nullable = false)
    private LocalDateTime answeredAt;

    public static TimeAttackAnswer create(
            TimeAttackSession timeAttackSession,
            QuizQuestion question,
            QuizOption selectedOption,
            Boolean correct,
            LocalDateTime answeredAt
    ) {
        return TimeAttackAnswer.builder()
                .timeAttackSession(timeAttackSession)
                .question(question)
                .selectedOption(selectedOption)
                .correct(correct)
                .answeredAt(answeredAt)
                .build();
    }
}
