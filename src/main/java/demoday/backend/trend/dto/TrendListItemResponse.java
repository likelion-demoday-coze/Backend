package demoday.backend.trend.dto;

import demoday.backend.trend.domain.EconomicTrend;

public record TrendListItemResponse(Long trendId, int displayOrder, String title) {
    public static TrendListItemResponse from(EconomicTrend trend) {
        return new TrendListItemResponse(trend.getEconomicTrendId(), trend.getDisplayOrder(), trend.getTitle());
    }
}
