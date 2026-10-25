package demoday.backend.payment.scheduler;

import demoday.backend.payment.service.PaymentFulfillmentRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFulfillmentRetryScheduler {

    private final PaymentFulfillmentRetryService retryService;

    @Scheduled(
            fixedDelayString =
                    "${app.payment.fulfillment-retry-delay-ms:300000}"
    )
    public void retryPendingFulfillments() {
        retryService.retryPendingFulfillments();
    }
}
