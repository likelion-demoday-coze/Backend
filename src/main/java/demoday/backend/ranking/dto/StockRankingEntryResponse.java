package demoday.backend.ranking.dto;

import demoday.backend.ranking.repository.projection.StockRankingRow;

import java.math.BigDecimal;

public record StockRankingEntryResponse(
        long rankingPosition,
        Long memberId,
        String nickname,
        BigDecimal currentStock
) {

    public static StockRankingEntryResponse from(
            StockRankingRow row
    ) {
        return new StockRankingEntryResponse(
                row.getRankingPosition(),
                row.getMemberId(),
                row.getNickname(),
                row.getCurrentStock()
        );
    }
}
