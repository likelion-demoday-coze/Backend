package demoday.backend.dailyquiz.domain;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.quiz.code.QuizCategory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "daily_quiz_session")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DailyQuizSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_quiz_session_id")
    private Long dailyQuizSessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuizCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DailyQuizSessionStatus status;

    @Column(name = "start_stock", nullable = false, precision = 30, scale = 2)
    private BigDecimal startStock;

    @Column(name = "end_stock", precision = 30, scale = 2)
    private BigDecimal endStock;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "pass_applied", nullable = false)
    private Boolean passApplied;

    public static DailyQuizSession create(
            Member member,
            QuizCategory category,
            DailyQuizSessionStatus status,
            BigDecimal startStock,
            LocalDateTime startedAt,
            LocalDateTime expiresAt,
            Boolean passApplied
    ) {
        return DailyQuizSession.builder()
                .member(member)
                .category(category)
                .status(status)
                .startStock(startStock)
                .startedAt(startedAt)
                .expiresAt(expiresAt)
                .passApplied(passApplied)
                .build();
    }
}
