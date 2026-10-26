package demoday.backend.payment.metric;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentMetricsTest {

    @Test
    void recordsPaymentCountersAndTimers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentMetrics metrics = new PaymentMetrics(registry);

        metrics.recordOrderCreated();
        metrics.recordFeedbackSuccess();
        metrics.recordFeedbackRejected();
        metrics.recordFeedbackFailure();
        metrics.recordDuplicateFeedbackBlocked();
        metrics.recordFulfillmentSuccess();
        metrics.recordFulfillmentFailure();
        metrics.recordCancellationSuccess();
        metrics.recordCancellationRejected();
        metrics.recordCancellationFailure();
        metrics.recordFeedbackProcessing(() -> {});
        String result = metrics.recordCancellationProcessing(() -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(registry.get("payment.order.created").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.feedback").tag("result", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.feedback").tag("result", "rejected").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.feedback").tag("result", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.feedback.duplicate.blocked").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.fulfillment").tag("result", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.fulfillment").tag("result", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.cancellation").tag("result", "success").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.cancellation").tag("result", "rejected").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.cancellation").tag("result", "failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("payment.feedback.processing").timer().count()).isEqualTo(1);
        assertThat(registry.get("payment.cancellation.processing").timer().count()).isEqualTo(1);
    }
}
