package demoday.backend.payment.client.dto;

public record PayAppCancelResponse(
        boolean success,
        String errorCode,
        String errorMessage
) {
}
