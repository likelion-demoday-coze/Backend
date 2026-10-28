package demoday.backend.ranking.repository.projection;

import java.math.BigDecimal;

public interface RankingWinnerRow {

    Long getMemberId();
    Long getRankingPosition();
    BigDecimal getRankingResultValue();
}
