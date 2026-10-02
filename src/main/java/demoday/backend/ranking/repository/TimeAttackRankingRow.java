package demoday.backend.ranking.repository;

import java.math.BigDecimal;

public interface TimeAttackRankingRow {

    Long getMemberId();
    String getNickname();
    Integer getCorrectCount();
    Long getRankingPosition();
    Long getTotalMemberCount();
    BigDecimal getAverageCorrectCount();
}
