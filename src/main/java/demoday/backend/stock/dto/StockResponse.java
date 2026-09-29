package demoday.backend.stock.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record StockResponse(
        @Schema(description = "현재 주가. 소수점 둘째 자리까지 저장하며 신규 회원의 초기 주가는 100.00입니다.", example = "100.00")
        BigDecimal currentStock
) {
}
