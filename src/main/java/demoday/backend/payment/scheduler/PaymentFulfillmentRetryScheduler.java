package demoday.backend.payment.scheduler;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import demoday.backend.global.logging.JobRunContext;
import demoday.backend.payment.service.PaymentFulfillmentRetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentFulfillmentRetryScheduler {

    private final PaymentFulfillmentRetryService retryService;
    private final JobRunContext jobRunContext;

    @Scheduled(
            fixedDelayString =
                    "${app.payment.fulfillment-retry-delay-ms:300000}"
    )
    @SchedulerLock(name = "payment-fulfillment-retry", lockAtLeastFor = "${app.scheduling.lock-at-least-for:1m}")
    public void retryPendingFulfillments() {
        jobRunContext.run(
                "payment-fulfillment-retry",
                retryService::retryPendingFulfillments
        );
    }
}
