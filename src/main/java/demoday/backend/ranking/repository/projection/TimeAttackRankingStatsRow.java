package demoday.backend.ranking.repository.projection;

import java.math.BigDecimal;

public interface TimeAttackRankingStatsRow {

    Long getTotalMemberCount();
    BigDecimal getAverageCorrectCount();
}
