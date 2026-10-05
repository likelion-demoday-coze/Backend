package demoday.backend.trend.dto;

import java.time.LocalDate;
import java.util.List;

/** AI 답변에서 파싱한 저장 후보. 검증하기 전에는 엔티티로 저장하지 않는다. */
public record GeneratedTrendContent(List<Item> items) {
    public record Item(String title, String summary, List<Term> terms, List<Reference> references) {}
    public record Term(String name, String description) {}
    public record Reference(String title, String url, String publisher, LocalDate publishedDate) {}
}
