package demoday.backend.stock.dto;

import demoday.backend.stock.domain.StockChange;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;

public record StockChangePageResponse(
        List<StockChangeResponse> content,
        @Schema(description = "0부터 시작하는 현재 페이지 번호") int page,
        @Schema(description = "요청한 페이지 크기") int size,
        @Schema(description = "본인의 전체 변동 내역 수") long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static StockChangePageResponse from(Page<StockChange> changes) {
        return new StockChangePageResponse(
                changes.getContent().stream().map(StockChangeResponse::from).toList(),
                changes.getNumber(), changes.getSize(), changes.getTotalElements(),
                changes.getTotalPages(), changes.hasNext()
        );
    }
}
