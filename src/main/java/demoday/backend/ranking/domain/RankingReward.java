package demoday.backend.ranking.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.ranking.code.RankingType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    public static RankingReward create(
            Member member,
            RankingType rankingType,
            LocalDate rankingDate,
            Integer rankingPosition
    ) {
        return RankingReward.builder()
                .member(member)
                .rankingType(rankingType)
                .rankingDate(rankingDate)
                .rankingPosition(rankingPosition)
                .build();
    }
}
