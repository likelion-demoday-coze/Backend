package demoday.backend.stock.dto;

import demoday.backend.stock.domain.StockDailySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockHistoryResponse(
        @Schema(description = "조회 시작일 (포함)") LocalDate from,
        @Schema(description = "조회 종료일 (포함)") LocalDate to,
        @Schema(description = "저장된 일별 스냅샷. 날짜 오름차순이며 누락 날짜는 채우지 않습니다.")
        List<Point> points
) {
    public static StockHistoryResponse from(LocalDate from, LocalDate to, List<StockDailySnapshot> snapshots) {
        return new StockHistoryResponse(from, to, snapshots.stream()
                .map(snapshot -> new Point(snapshot.getSnapshotDate(), snapshot.getStockValue()))
                .toList());
    }

    @Schema(name = "StockHistoryPoint")
    public record Point(
            @Schema(description = "스냅샷 기준 날짜 (Asia/Seoul)", example = "2026-09-29")
            LocalDate date,
            @Schema(description = "스냅샷 저장 당시 주가. 실시간 현재 주가와 다를 수 있습니다.", example = "110.25")
            BigDecimal stockValue
    ) {
    }
}
