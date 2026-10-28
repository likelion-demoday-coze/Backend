package demoday.backend.ranking.dto;

import demoday.backend.ranking.code.RankingType;
import demoday.backend.ranking.domain.RankingReward;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RankingRewardResponse(
        Long rankingRewardId,
        RankingType rankingType,
        LocalDate rankingDate,
        int rankingPosition,
        long rewardFishAmount,
        long fishBalanceBefore,
        long fishBalanceAfter,
        BigDecimal stockClosingValue,
        Integer timeAttackCorrectCount
) {

    public static RankingRewardResponse from(
            RankingReward reward
    ) {
        boolean stock =
                reward.getRankingType() == RankingType.STOCK;

        return new RankingRewardResponse(
                reward.getRankingRewardId(),
                reward.getRankingType(),
                reward.getRankingDate(),
                reward.getRankingPosition(),
                reward.getRewardFishAmount(),
                reward.getFishBalanceBefore(),
                reward.getFishBalanceAfter(),
                stock
                        ? reward.getRankingResultValue()
                        : null,
                stock
                        ? null
                        : reward.getRankingResultValue()
                        .intValueExact()
        );
    }
}
