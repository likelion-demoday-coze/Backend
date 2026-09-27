package demoday.backend.dailyquiz.domain;

import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.global.exception.ProjectException;
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
            BigDecimal startStock,
            LocalDateTime startedAt,
            Boolean passApplied
    ) {
        if (startStock == null) {
            throw new IllegalArgumentException("시작 주가는 필수입니다.");
        }

        if (startedAt == null) {
            throw new IllegalArgumentException("세션 시작 시각은 필수입니다.");
        }

        LocalDateTime expiresAt = startedAt.toLocalDate()
                .plusDays(1)
                .atStartOfDay();

        return DailyQuizSession.builder()
                .member(member)
                .category(category)
                .status(DailyQuizSessionStatus.IN_PROGRESS)
                .startStock(startStock)
                .startedAt(startedAt)
                .expiresAt(expiresAt)
                .passApplied(passApplied)
                .build();
    }

    // 전달 받은 서버 시각 세션 만료 시각에 도달했는지 확인
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    public void completeOriginal() {
        if (status != DailyQuizSessionStatus.IN_PROGRESS) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_SESSION_STATE);
        }

        status = DailyQuizSessionStatus.ORIGINAL_COMPLETED;
    }

    // 원본 문제 5개 모두 제출 시 세션 상태 변경
    public void complete(BigDecimal endStock) {
        if (status != DailyQuizSessionStatus.ORIGINAL_COMPLETED) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_SESSION_STATE);
        }

        if (endStock == null) {
            throw new IllegalArgumentException("종료 주가는 필수입니다.");
        }

        this.endStock = endStock;
        status = DailyQuizSessionStatus.COMPLETED;
    }

    // 생성 다음 날 00:00 KST에 도달한 세션을 만료 상태로 변경
    public void expire(LocalDateTime now) {
        if (!isExpired(now)) {
            throw new ProjectException(DailyQuizErrorCode.SESSION_NOT_EXPIRED);
        }

        if (status == DailyQuizSessionStatus.COMPLETED) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_SESSION_STATE);
        }

        status = DailyQuizSessionStatus.EXPIRED;
    }
}
