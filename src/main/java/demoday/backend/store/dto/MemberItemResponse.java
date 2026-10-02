package demoday.backend.store.dto;

import demoday.backend.store.domain.MemberItem;
import demoday.backend.store.domain.StoreItem;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberItemResponse(
        Long itemId,
        String itemCode,
        String name,
        @Schema(description = "현재 보유 수량", example = "2")
        int quantity
) {
    public static MemberItemResponse from(MemberItem memberItem) {
        StoreItem item = memberItem.getItem();
        return new MemberItemResponse(item.getItemId(), item.getItemCode(), item.getName(), memberItem.getQuantity());
    }
}
