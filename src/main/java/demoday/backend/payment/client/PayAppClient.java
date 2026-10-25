package demoday.backend.payment.client;

import demoday.backend.payment.client.dto.PayAppPaymentRequest;
import demoday.backend.payment.client.dto.PayAppPaymentResponse;

public interface PayAppClient {

    PayAppPaymentResponse requestPayment(
            PayAppPaymentRequest request
    );
}
