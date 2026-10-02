package demoday.backend.ranking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockRankingResponse(
        LocalDateTime rankedAt,
        long totalMemberCount,
        BigDecimal averageStock,
        List<StockRankingEntryResponse> rankings,
        MyStockRankingResponse myRanking,
        RankingPageResponse page
) {
}
