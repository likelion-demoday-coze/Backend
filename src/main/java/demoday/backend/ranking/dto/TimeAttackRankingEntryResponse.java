package demoday.backend.ranking.dto;

import demoday.backend.ranking.repository.projection.TimeAttackRankingRow;

public record TimeAttackRankingEntryResponse(
        long rankingPosition,
        Long memberId,
        String nickname,
        int correctCount
) {

    public static TimeAttackRankingEntryResponse from(
            TimeAttackRankingRow row
    ) {
        return new TimeAttackRankingEntryResponse(
                row.getRankingPosition(),
                row.getMemberId(),
                row.getNickname(),
                row.getCorrectCount()
        );
    }
}
