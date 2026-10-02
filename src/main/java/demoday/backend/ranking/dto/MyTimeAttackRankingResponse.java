package demoday.backend.ranking.dto;

import demoday.backend.ranking.repository.projection.TimeAttackRankingRow;

import java.math.BigDecimal;

public record MyTimeAttackRankingResponse(
        long rankingPosition,
        BigDecimal topPercent,
        int correctCount
) {

    public static MyTimeAttackRankingResponse of(
            TimeAttackRankingRow row,
            BigDecimal topPercent
    ) {
        return new MyTimeAttackRankingResponse(
                row.getRankingPosition(),
                topPercent,
                row.getCorrectCount()
        );
    }
}
