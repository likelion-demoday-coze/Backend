package demoday.backend.ranking.repository.projection;

import java.math.BigDecimal;

public interface StockRankingRow {

    Long getMemberId();
    String getNickname();
    BigDecimal getCurrentStock();
    Long getRankingPosition();
}
