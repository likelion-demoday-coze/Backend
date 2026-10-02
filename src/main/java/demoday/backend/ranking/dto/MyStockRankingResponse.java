package demoday.backend.ranking.dto;

import demoday.backend.ranking.repository.projection.StockRankingRow;

import java.math.BigDecimal;

public record MyStockRankingResponse(
        long rankingPosition,
        BigDecimal topPercent,
        BigDecimal currentStock
) {

    public static MyStockRankingResponse of(
            StockRankingRow row,
            BigDecimal topPercent
    ) {
        return new MyStockRankingResponse(
                row.getRankingPosition(),
                topPercent,
                row.getCurrentStock()
        );
    }
}
