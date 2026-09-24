package demoday.backend.activity.domain;

import demoday.backend.activity.code.LearningStatus;
import demoday.backend.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "member_daily_activity",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_daily_activity_member_date",
                columnNames = {"member_id", "activity_date"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberDailyActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_activity_id")
    private Long dailyActivityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "original_answer_count", nullable = false)
    private Integer originalAnswerCount;

    @Column(name = "stock_opportunity_used_count", nullable = false)
    private Integer stockOpportunityUsedCount;

    @Column(name = "time_attack_attempt_count", nullable = false)
    private Integer timeAttackAttemptCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_status", nullable = false)
    private LearningStatus learningStatus;

    @Column(name = "learned_at")
    private LocalDateTime learnedAt;

    public static MemberDailyActivity create(Member member, LocalDate activityDate) {
        return MemberDailyActivity.builder()
                .member(member)
                .activityDate(activityDate)
                .originalAnswerCount(0)
                .stockOpportunityUsedCount(0)
                .timeAttackAttemptCount(0)
                .learningStatus(LearningStatus.NONE)
                .build();
    }
}
