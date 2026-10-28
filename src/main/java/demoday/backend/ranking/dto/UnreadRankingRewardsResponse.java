package demoday.backend.ranking.dto;

import demoday.backend.ranking.domain.RankingReward;

import java.util.List;

public record UnreadRankingRewardsResponse(
        List<RankingRewardResponse> rewards
) {

    public static UnreadRankingRewardsResponse from(
            List<RankingReward> rewards
    ) {
        return new UnreadRankingRewardsResponse(
                rewards.stream()
                        .map(RankingRewardResponse::from)
                        .toList()
        );
    }
}
