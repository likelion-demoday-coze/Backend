package demoday.backend.payment.scheduler;

import demoday.backend.global.logging.JobRunContext;
import demoday.backend.payment.service.PaymentOrderExpirationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentOrderExpirationScheduler {

    private final PaymentOrderExpirationService expirationService;
    private final JobRunContext jobRunContext;

    @Scheduled(
            fixedDelayString =
                    "${app.payment.order.expiration-scan-delay-ms:60000}"
    )
    public void expirePendingOrders() {
        jobRunContext.run(
                "payment-order-expiration",
                expirationService::expirePendingOrders
        );
    }
}
