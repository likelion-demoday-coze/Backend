package demoday.backend.trend.dto;

import demoday.backend.trend.code.TrendContentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TodayTrendsResponse(
        @Schema(description = "조회 기준 날짜 (KST)") LocalDate date,
        @Schema(description = "제공한 콘텐츠의 생성 기준 날짜. 준비 중이면 null") LocalDate contentDate,
        @Schema(description = "저장된 생성 기준 시각 (KST). 준비 중이면 null") LocalDateTime generatedAt,
        TrendContentStatus status,
        @Schema(description = "준비 중 안내. 콘텐츠 제공 시 null") String message,
        List<TrendListItemResponse> items
) {}
