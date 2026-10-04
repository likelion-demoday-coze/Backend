package demoday.backend.ranking.repository.projection;

import java.math.BigDecimal;

public interface StockClosingValueRow {

    Long getMemberId();
    BigDecimal getClosingStock();
}
