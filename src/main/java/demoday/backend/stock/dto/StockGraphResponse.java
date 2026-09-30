package demoday.backend.stock.dto;

import demoday.backend.stock.code.StockGraphPeriod;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record StockGraphResponse(
        StockGraphPeriod period,
        @Schema(description = "그래프 시작 시각 (KST)") LocalDateTime from,
        @Schema(description = "조회 기준 시각 (KST)") LocalDateTime asOf,
        @Schema(description = "가입 시각이 없는 기존 회원의 시작 기준을 추정했는지 여부") boolean estimatedStart,
        BigDecimal startStock,
        BigDecimal currentStock,
        BigDecimal changeAmount,
        @Schema(description = "시작 주가 대비 등락률(%). 시작 주가가 0이면 null") BigDecimal changeRate,
        List<Point> points
) {
    @Schema(name = "StockGraphPoint")
    public record Point(LocalDateTime timestamp, BigDecimal stockValue) {
    }
}
