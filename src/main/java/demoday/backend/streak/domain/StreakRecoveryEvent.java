package demoday.backend.streak.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.streak.code.StreakRecoveryStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "streak_recovery_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_streak_recovery_event_member_date",
                columnNames = {"member_id", "missed_date"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StreakRecoveryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "streak_recovery_event_id")
    private Long streakRecoveryEventId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "missed_date", nullable = false)
    private LocalDate missedDate;

    @Column(name = "streak_before_penalty", nullable = false)
    private Integer streakBeforePenalty;

    @Column(name = "stock_before_penalty", nullable = false, precision = 30, scale = 2)
    private BigDecimal stockBeforePenalty;

    @Column(name = "stock_after_penalty", nullable = false, precision = 30, scale = 2)
    private BigDecimal stockAfterPenalty;

    // missedDate는 쉬기 시작한 날, appliedAt은 서버가 실제로 하락을 반영한 시각이다.
    @Column(name = "penalty_applied_at", nullable = false)
    private LocalDateTime penaltyAppliedAt;

    @Column(name = "penalty_stock_change_id", unique = true)
    private Long penaltyStockChangeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StreakRecoveryStatus status;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    public static StreakRecoveryEvent create(
            Member member,
            LocalDate missedDate,
            Integer streakBeforePenalty,
            BigDecimal stockBeforePenalty,
            BigDecimal stockAfterPenalty,
            LocalDateTime penaltyAppliedAt
    ) {
        return StreakRecoveryEvent.builder()
                .member(member)
                .missedDate(missedDate)
                .streakBeforePenalty(streakBeforePenalty)
                .stockBeforePenalty(stockBeforePenalty)
                .stockAfterPenalty(stockAfterPenalty)
                .penaltyAppliedAt(penaltyAppliedAt)
                .status(StreakRecoveryStatus.AVAILABLE)
                .build();
    }

    public void recordPenaltyStockChange(Long stockChangeId) {
        if (stockChangeId == null || stockChangeId <= 0 || penaltyStockChangeId != null) {
            throw new IllegalArgumentException("하락 주가 이력은 한 번만 연결할 수 있습니다.");
        }
        penaltyStockChangeId = stockChangeId;
    }
}
