package demoday.backend.ranking.repository.projection;

import java.math.BigDecimal;

public interface StockRankingStatsRow {

    Long getTotalMemberCount();
    BigDecimal getAverageStock();
}
