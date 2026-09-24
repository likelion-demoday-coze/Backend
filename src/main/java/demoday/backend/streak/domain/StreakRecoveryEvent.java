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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StreakRecoveryStatus status;

    @Column(name = "recovered_at")
    private LocalDateTime recoveredAt;

    public static StreakRecoveryEvent create(
            Member member,
            LocalDate missedDate,
            Integer streakBeforePenalty
    ) {
        return StreakRecoveryEvent.builder()
                .member(member)
                .missedDate(missedDate)
                .streakBeforePenalty(streakBeforePenalty)
                .status(StreakRecoveryStatus.AVAILABLE)
                .build();
    }
}
