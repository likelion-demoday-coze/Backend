package demoday.backend.payment.client;

import demoday.backend.payment.client.dto.PayAppPaymentRequest;
import demoday.backend.payment.client.dto.PayAppPaymentResponse;
import demoday.backend.payment.client.dto.PayAppCancelResponse;

public interface PayAppClient {

    PayAppPaymentResponse requestPayment(
            PayAppPaymentRequest request
    );

    PayAppCancelResponse cancelPayment(
            String transactionId,
            String cancellationMemo
    );
}
