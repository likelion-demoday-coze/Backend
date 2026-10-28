package demoday.backend.ranking.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.ranking.code.RankingType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "ranking_reward",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ranking_reward_member_type_date",
                columnNames = {"member_id", "ranking_type", "ranking_date"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RankingReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ranking_reward_id")
    private Long rankingRewardId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "ranking_type", nullable = false)
    private RankingType rankingType;

    @Column(name = "ranking_date", nullable = false)
    private LocalDate rankingDate;

    @Column(name = "ranking_position", nullable = false)
    private Integer rankingPosition;

    @Column(
            name = "ranking_result_value",
            nullable = false,
            precision = 30,
            scale = 2
    )
    private BigDecimal rankingResultValue;

    @Column(name = "reward_fish_amount", nullable = false)
    private Long rewardFishAmount;

    @Column(name = "fish_balance_before", nullable = false)
    private Long fishBalanceBefore;

    @Column(name = "fish_balance_after", nullable = false)
    private Long fishBalanceAfter;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    public static RankingReward create(
            Member member,
            RankingType rankingType,
            LocalDate rankingDate,
            Integer rankingPosition,
            BigDecimal rankingResultValue,
            long rewardFishAmount,
            long fishBalanceBefore,
            long fishBalanceAfter
    ) {
        if (rankingResultValue == null) {
            throw new IllegalArgumentException("랭킹 결과값은 필수입니다.");
        }

        return RankingReward.builder()
                .member(member)
                .rankingType(rankingType)
                .rankingDate(rankingDate)
                .rankingPosition(rankingPosition)
                .rankingResultValue(rankingResultValue)
                .rewardFishAmount(rewardFishAmount)
                .fishBalanceBefore(fishBalanceBefore)
                .fishBalanceAfter(fishBalanceAfter)
                .build();
    }

    public void markAsRead(LocalDateTime readAt) {
        if (this.readAt != null) {
            return;
        }

        if (readAt == null) {
            throw new IllegalArgumentException("확인 시각은 필수입니다.");
        }

        this.readAt = readAt;
    }
}
