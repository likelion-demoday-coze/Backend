package demoday.backend.payment.client.dto;

public record PayAppPaymentResponse(
        boolean success,
        String errorCode,
        String errorMessage,
        String transactionId,
        String paymentUrl
) {
}
