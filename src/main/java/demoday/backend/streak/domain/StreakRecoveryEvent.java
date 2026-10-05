package demoday.backend.streak.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.code.StreakRecoveryMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "streak_recovery_event",
        uniqueConstraints = {@UniqueConstraint(
                name = "uk_streak_recovery_event_member_date",
                columnNames = {"member_id", "missed_date"}
        ), @UniqueConstraint(name = "uk_streak_recovery_event_member_request_date", columnNames = {"member_id", "request_date"})}
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

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_method")
    private StreakRecoveryMethod recoveryMethod;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "request_date")
    private LocalDate requestDate;

    @Column(name = "expired_at")
    private LocalDateTime expiredAt;

    @Column(name = "consumed_member_item_id")
    private Long consumedMemberItemId;

    @Column(name = "quantity_after_request")
    private Integer quantityAfterRequest;

    @Column(name = "recovery_stock_change_id", unique = true)
    private Long recoveryStockChangeId;

    @Column(name = "stock_after_recovery", precision = 30, scale = 2)
    private BigDecimal stockAfterRecovery;

    @Column(name = "streak_after_recovery")
    private Integer streakAfterRecovery;

    @Column(name = "penalty_notification_acknowledged_at")
    private LocalDateTime penaltyNotificationAcknowledgedAt;

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

    public void requestRecovery(StreakRecoveryMethod method, LocalDateTime now, Long memberItemId, Integer quantityAfter) {
        if (status != StreakRecoveryStatus.AVAILABLE || method == null || now == null) {
            throw new IllegalStateException("복구 신청 상태가 올바르지 않습니다.");
        }
        recoveryMethod = method;
        requestedAt = now;
        requestDate = now.toLocalDate();
        consumedMemberItemId = memberItemId;
        quantityAfterRequest = quantityAfter;
        status = StreakRecoveryStatus.PENDING;
    }

    public boolean expireIfOverdue(LocalDateTime now) {
        if (status == StreakRecoveryStatus.PENDING && !requestedAt.toLocalDate().equals(now.toLocalDate())
                && now.isAfter(requestedAt)) {
            status = StreakRecoveryStatus.EXPIRED;
            expiredAt = now;
            return true;
        }
        return false;
    }

    public void completeRecovery(Long changeId, BigDecimal stockAfter, int streakAfter, LocalDateTime now) {
        if (status != StreakRecoveryStatus.PENDING || !requestedAt.toLocalDate().equals(now.toLocalDate())) {
            throw new IllegalStateException("신청 당일에만 복구를 완료할 수 있습니다.");
        }
        recoveryStockChangeId = changeId;
        stockAfterRecovery = stockAfter;
        streakAfterRecovery = streakAfter;
        recoveredAt = now;
        status = StreakRecoveryStatus.RECOVERED;
    }

    /** 알림을 실제로 표시한 클라이언트가 확인을 요청한다. 재요청은 최초 시각을 유지한다. */
    public void acknowledgePenaltyNotification(LocalDateTime now) {
        if (now == null) throw new IllegalArgumentException("알림 확인 시각은 필수입니다.");
        if (penaltyNotificationAcknowledgedAt == null) penaltyNotificationAcknowledgedAt = now;
    }

    /** 스케줄러가 지연되어도 읽기 응답에서는 완료 기한을 적용한다. DB 상태를 변경하지 않는다. */
    public StreakRecoveryStatus recoveryStatusAt(LocalDateTime now) {
        if (status == StreakRecoveryStatus.PENDING
                && !now.isBefore(requestedAt.toLocalDate().plusDays(1).atStartOfDay())) {
            return StreakRecoveryStatus.EXPIRED;
        }
        return status;
    }
}
