package demoday.backend.store.dto;

import demoday.backend.store.domain.StoreItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record StoreItemResponse(
        Long itemId,
        String itemCode,
        String name,
        @Schema(description = "아이템 1개 구매에 필요한 생선 수량", example = "200")
        int fishPrice,
        String description
) {
    public static StoreItemResponse from(StoreItem item) {
        return new StoreItemResponse(item.getItemId(), item.getItemCode(), item.getName(), item.getFishPrice(), item.getDescription());
    }
}
