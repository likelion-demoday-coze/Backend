package demoday.backend.fish.dto;

import demoday.backend.fish.domain.FishTransaction;
import org.springframework.data.domain.Page;

import java.util.List;

public record FishTransactionPageResponse(
        List<FishTransactionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static FishTransactionPageResponse from(Page<FishTransaction> transactions) {
        return new FishTransactionPageResponse(
                transactions.getContent().stream().map(FishTransactionResponse::from).toList(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages(),
                transactions.hasNext()
        );
    }
}
