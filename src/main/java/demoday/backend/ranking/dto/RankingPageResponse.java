package demoday.backend.ranking.dto;

public record RankingPageResponse(
        int page,
        int size,
        boolean hasNext
) {
}
