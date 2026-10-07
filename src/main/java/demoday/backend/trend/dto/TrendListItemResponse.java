package demoday.backend.trend.dto;

import demoday.backend.trend.domain.EconomicTrend;
import demoday.backend.trend.code.TrendCategory;
import io.swagger.v3.oas.annotations.media.Schema;

public record TrendListItemResponse(Long trendId, int displayOrder, String title,
        @Schema(description = "경제 분야 8개 또는 OTHER. 기존 미분류 콘텐츠는 OTHER") TrendCategory category,
        @Schema(description = "카테고리 한글 표시명") String categoryName) {
    public static TrendListItemResponse from(EconomicTrend trend) {
        var category = TrendCategory.forLegacy(trend.getCategory());
        return new TrendListItemResponse(trend.getEconomicTrendId(), trend.getDisplayOrder(), trend.getTitle(),
                category, category.getDisplayName());
    }
}
