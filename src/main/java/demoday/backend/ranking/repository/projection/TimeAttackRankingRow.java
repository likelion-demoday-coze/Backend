package demoday.backend.ranking.repository.projection;

public interface TimeAttackRankingRow {

    Long getMemberId();
    String getNickname();
    Integer getCorrectCount();
    Long getRankingPosition();
}
