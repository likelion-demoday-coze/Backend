package demoday.backend.fish.dto;

import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.domain.FishTransaction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record FishTransactionResponse(
        Long transactionId,
        FishTransactionType transactionType,
        @Schema(description = "지급은 양수, 차감은 음수", example = "-50")
        long amount,
        @Schema(description = "해당 거래 직후의 잔액. 현재 잔액과 다를 수 있습니다.")
        long balanceAfter,
        Long referenceId,
        @Schema(description = "거래 발생 시각 (Asia/Seoul)")
        LocalDateTime createdAt
) {
    public static FishTransactionResponse from(FishTransaction transaction) {
        return new FishTransactionResponse(
                transaction.getFishTransactionId(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReferenceId(),
                transaction.getCreatedAt()
        );
    }
}
