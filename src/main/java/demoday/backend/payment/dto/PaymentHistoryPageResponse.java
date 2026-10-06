package demoday.backend.payment.dto;

import demoday.backend.payment.domain.Payment;
import org.springframework.data.domain.Page;

import java.util.List;

public record PaymentHistoryPageResponse(
        List<PaymentHistoryResponse> payments,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static PaymentHistoryPageResponse from(Page<Payment> result) {
        return new PaymentHistoryPageResponse(
                result.getContent().stream()
                        .map(PaymentHistoryResponse::from)
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
