package demoday.backend.stock.dto;

import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.domain.StockChange;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockChangeResponse(
        Long stockChangeId,
        StockChangeType changeType,
        @Schema(description = "변경 직전 주가", example = "100.00")
        BigDecimal stockBefore,
        @Schema(description = "변경 직후 주가. 현재 주가와 다를 수 있습니다.", example = "105.00")
        BigDecimal stockAfter,
        @Schema(description = "변동을 발생시킨 업무의 참조 ID. 없으면 null입니다.")
        Long referenceId,
        @Schema(description = "변동 발생 시각 (Asia/Seoul)")
        LocalDateTime createdAt
) {
    public static StockChangeResponse from(StockChange change) {
        return new StockChangeResponse(
                change.getStockChangeId(), change.getChangeType(),
                change.getStockBefore(), change.getStockAfter(),
                change.getReferenceId(), change.getCreatedAt()
        );
    }
}
