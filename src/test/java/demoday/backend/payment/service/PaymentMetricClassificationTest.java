package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.PayAppClient;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.config.PayAppProperties;
import demoday.backend.payment.metric.PaymentMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentMetricClassificationTest {

    @Test
    void invalidFeedbackIsRecordedAsRejected() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentMetrics metrics = new PaymentMetrics(registry);
        PayAppFeedbackService service = new PayAppFeedbackService(
                mock(PayAppProperties.class),
                mock(PaymentProcessingTransactionService.class),
                mock(PaymentCancellationTransactionService.class),
                mock(PaymentCancellationService.class),
                metrics
        );

        assertThatThrownBy(() -> service.process(null))
                .isInstanceOf(ProjectException.class);

        assertThat(counter(registry, "payment.feedback", "rejected"))
                .isEqualTo(1);
        assertThat(counter(registry, "payment.feedback", "failure"))
                .isZero();
    }

    @Test
    void invalidCancellationRequestIsRecordedAsRejected() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentMetrics metrics = new PaymentMetrics(registry);
        PaymentCancellationService service = new PaymentCancellationService(
                mock(PaymentCancellationTransactionService.class),
                mock(PayAppClient.class),
                metrics,
                Clock.systemUTC()
        );

        assertThatThrownBy(() -> service.cancel(null, 1L))
                .isInstanceOf(ProjectException.class);

        assertThat(counter(registry, "payment.cancellation", "rejected"))
                .isEqualTo(1);
        assertThat(counter(registry, "payment.cancellation", "failure"))
                .isZero();
    }

    @Test
    void serverSideCancellationErrorIsRecordedAsFailure() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        PaymentMetrics metrics = new PaymentMetrics(registry);
        PaymentCancellationTransactionService transactionService =
                mock(PaymentCancellationTransactionService.class);
        PaymentCancellationService service = new PaymentCancellationService(
                transactionService,
                mock(PayAppClient.class),
                metrics,
                Clock.systemUTC()
        );

        when(transactionService.prepare(
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.eq(1L),
                org.mockito.ArgumentMatchers.any()
        )).thenThrow(new ProjectException(
                PaymentErrorCode.FULFILLMENT_FAILED
        ));

        assertThatThrownBy(() -> service.cancel(1L, 1L))
                .isInstanceOf(ProjectException.class);

        assertThat(counter(registry, "payment.cancellation", "rejected"))
                .isZero();
        assertThat(counter(registry, "payment.cancellation", "failure"))
                .isEqualTo(1);
    }

    private double counter(
            SimpleMeterRegistry registry,
            String name,
            String result
    ) {
        return registry.get(name)
                .tag("result", result)
                .counter()
                .count();
    }
}
