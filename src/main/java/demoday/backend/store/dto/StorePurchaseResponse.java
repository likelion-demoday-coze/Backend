package demoday.backend.store.dto;

import demoday.backend.store.domain.StorePurchase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record StorePurchaseResponse(Long purchaseId, Long itemId, int quantity, int unitPrice, long totalPrice,
        @Schema(description = "해당 구매 직후 잔액. 재요청 시에도 최초 값을 반환") long balanceAfter,
        @Schema(description = "해당 구매 직후 보유 수량. 재요청 시에도 최초 값을 반환") int quantityAfter,
        LocalDateTime createdAt) {
    public static StorePurchaseResponse from(StorePurchase purchase) {
        return new StorePurchaseResponse(purchase.getPurchaseId(), purchase.getItem().getItemId(),
                purchase.getQuantity(), purchase.getUnitPrice(), purchase.getTotalPrice(),
                purchase.getBalanceAfter(), purchase.getQuantityAfter(), purchase.getCreatedAt());
    }
}
