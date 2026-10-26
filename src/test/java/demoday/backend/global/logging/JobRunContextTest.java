package demoday.backend.global.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobRunContextTest {

    private final JobRunContext jobRunContext =
            new JobRunContext();

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void jobRunIdAndJobNameAreAvailableDuringExecution() {
        AtomicReference<String> capturedJobRunId =
                new AtomicReference<>();
        AtomicReference<String> capturedJobName =
                new AtomicReference<>();

        jobRunContext.run(
                "payment-retry",
                () -> {
                    capturedJobRunId.set(
                            MDC.get("jobRunId")
                    );
                    capturedJobName.set(
                            MDC.get("jobName")
                    );
                }
        );

        assertThat(capturedJobRunId.get()).isNotBlank();
        assertThat(capturedJobName.get())
                .isEqualTo("payment-retry");
        assertThat(MDC.get("jobRunId")).isNull();
        assertThat(MDC.get("jobName")).isNull();
    }

    @Test
    void mdcIsRestoredWhenJobFails() {
        MDC.put("jobRunId", "previous-run");
        MDC.put("jobName", "previous-job");

        assertThatThrownBy(() ->
                jobRunContext.run(
                        "failing-job",
                        () -> {
                            throw new IllegalStateException(
                                    "실패"
                            );
                        }
                )
        ).isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get("jobRunId"))
                .isEqualTo("previous-run");
        assertThat(MDC.get("jobName"))
                .isEqualTo("previous-job");
    }
}
