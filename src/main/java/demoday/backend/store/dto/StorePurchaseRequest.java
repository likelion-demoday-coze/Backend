package demoday.backend.store.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record StorePurchaseRequest(
        @NotNull @Min(1) Integer quantity,
        @NotNull @Schema(description = "같은 구매 재요청에는 같은 UUID, 새로운 구매에는 새로운 UUID 사용") UUID requestId
) {}
