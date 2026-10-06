package demoday.backend.trend.dto;

import demoday.backend.trend.domain.EconomicTerm;
import demoday.backend.trend.domain.TrendReference;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TrendDetailResponse(
        Long trendId,
        @Schema(description = "콘텐츠 생성 기준 날짜 (KST)") LocalDate contentDate,
        @Schema(description = "저장된 생성 기준 시각 (KST)") LocalDateTime generatedAt,
        Integer displayOrder,
        String title,
        String summary,
        List<TermResponse> terms,
        List<ReferenceResponse> references
) {
    public record TermResponse(String name, String description) {
        public static TermResponse from(EconomicTerm term) {
            return new TermResponse(term.getName(), term.getDescription());
        }
    }

    public record ReferenceResponse(String title, String url, String publisher, LocalDate publishedDate) {
        public static ReferenceResponse from(TrendReference reference) {
            return new ReferenceResponse(reference.getTitle(), reference.getUrl(),
                    reference.getPublisher(), reference.getPublishedDate());
        }
    }
}
