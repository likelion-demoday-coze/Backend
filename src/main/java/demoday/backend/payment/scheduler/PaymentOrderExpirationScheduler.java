package demoday.backend.payment.scheduler;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

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
    @SchedulerLock(name = "payment-order-expiration", lockAtLeastFor = "${app.scheduling.lock-at-least-for:30s}")
    public void expirePendingOrders() {
        jobRunContext.run(
                "payment-order-expiration",
                expirationService::expirePendingOrders
        );
    }
}
