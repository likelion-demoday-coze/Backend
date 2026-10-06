package demoday.backend.payment.client;

import demoday.backend.payment.client.dto.KorpayConfirmResponse;

public interface KorpayClient {

    KorpayConfirmResponse confirm(String paymentKey);
}
