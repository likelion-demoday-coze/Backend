package demoday.backend.payment.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

@Component
public class PaymentMetrics {

    private final Counter orderCreated;
    private final Counter feedbackSucceeded;
    private final Counter feedbackFailed;
    private final Counter fulfillmentSucceeded;
    private final Counter fulfillmentFailed;
    private final Counter duplicateFeedbackBlocked;
    private final Counter cancellationSucceeded;
    private final Counter cancellationFailed;
    private final Timer feedbackProcessingTime;
    private final Timer cancellationProcessingTime;

    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.orderCreated = Counter.builder("payment.order.created")
                .description("생성된 결제 주문 수")
                .register(meterRegistry);

        this.feedbackSucceeded = Counter.builder("payment.feedback")
                .description("페이앱 Feedback 처리 결과")
                .tag("result", "success")
                .register(meterRegistry);

        this.feedbackFailed = Counter.builder("payment.feedback")
                .description("페이앱 Feedback 처리 결과")
                .tag("result", "failure")
                .register(meterRegistry);

        this.fulfillmentSucceeded = Counter.builder("payment.fulfillment")
                .description("결제 상품 지급 결과")
                .tag("result", "success")
                .register(meterRegistry);

        this.fulfillmentFailed = Counter.builder("payment.fulfillment")
                .description("결제 상품 지급 결과")
                .tag("result", "failure")
                .register(meterRegistry);

        this.duplicateFeedbackBlocked =
                Counter.builder("payment.feedback.duplicate.blocked")
                        .description("중복 Feedback 차단 수")
                        .register(meterRegistry);

        this.cancellationSucceeded = Counter.builder("payment.cancellation")
                .description("사용자 결제 취소 API 처리 결과")
                .tag("result", "success")
                .register(meterRegistry);

        this.cancellationFailed = Counter.builder("payment.cancellation")
                .description("사용자 결제 취소 API 처리 결과")
                .tag("result", "failure")
                .register(meterRegistry);

        this.feedbackProcessingTime =
                Timer.builder("payment.feedback.processing")
                        .description("결제 Feedback 처리 시간")
                        .publishPercentileHistogram()
                        .serviceLevelObjectives(
                                Duration.ofMillis(500),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(3)
                        )
                        .register(meterRegistry);

        this.cancellationProcessingTime =
                Timer.builder("payment.cancellation.processing")
                        .description("사용자 결제 취소 API 처리 시간")
                        .publishPercentileHistogram()
                        .serviceLevelObjectives(
                                Duration.ofMillis(500),
                                Duration.ofSeconds(1),
                                Duration.ofSeconds(3)
                        )
                        .register(meterRegistry);
    }

    public void recordOrderCreated() {
        orderCreated.increment();
    }

    public void recordFeedbackSuccess() {
        feedbackSucceeded.increment();
    }

    public void recordFeedbackFailure() {
        feedbackFailed.increment();
    }

    public void recordFulfillmentSuccess() {
        fulfillmentSucceeded.increment();
    }

    public void recordFulfillmentFailure() {
        fulfillmentFailed.increment();
    }

    public void recordDuplicateFeedbackBlocked() {
        duplicateFeedbackBlocked.increment();
    }

    public void recordCancellationSuccess() {
        cancellationSucceeded.increment();
    }

    public void recordCancellationFailure() {
        cancellationFailed.increment();
    }

    public void recordFeedbackProcessing(Runnable operation) {
        feedbackProcessingTime.record(operation);
    }

    public <T> T recordCancellationProcessing(Supplier<T> operation) {
        return cancellationProcessingTime.record(operation);
    }
}
