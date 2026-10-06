package demoday.backend.payment.dto;

public record KorpayCallbackRequest(

        String resultCode,

        String message,

        String paymentKey,

        String merchantId,

        String orderNumber,

        String amount,

        String reserved
) {
}
