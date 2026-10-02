package demoday.backend.ranking.repository;

import java.math.BigDecimal;

public interface StockRankingRow {

    Long getMemberId();
    String getNickname();
    BigDecimal getCurrentStock();
    Long getRankingPosition();
    Long getTotalMemberCount();
    BigDecimal getAverageStock();
}
