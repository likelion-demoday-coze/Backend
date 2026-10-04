package demoday.backend.ranking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record TimeAttackRankingResponse(
        LocalDate rankingDate,
        long totalMemberCount,
        BigDecimal averageCorrectCount,
        List<TimeAttackRankingEntryResponse> rankings,
        MyTimeAttackRankingResponse myRanking,
        RankingPageResponse page
) {
}
