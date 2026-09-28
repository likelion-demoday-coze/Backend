package demoday.backend.fish.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record FishBalanceResponse(
        @Schema(description = "실제 보유 생선 수량. 패스 이용 여부와 관계없이 실제 잔액을 반환합니다.", example = "100")
        long balance
) {
}
