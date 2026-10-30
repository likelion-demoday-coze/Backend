package demoday.backend.global.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.assertThat;

class GracefulJobExecutorTest {
    @Test
    @Timeout(10)
    void shutdownDrainsRunningJobWithoutExecutingFutureSchedules() throws Exception {
        var jobs = new GracefulJobExecutor();
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var completed = new AtomicBoolean();
        var futureRan = new AtomicBoolean();
        var closer = Executors.newSingleThreadExecutor();
        try {
            jobs.execute(() -> {
                started.countDown();
                try { release.await(); completed.set(true); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
            });
            jobs.schedule(() -> futureRan.set(true), 1, TimeUnit.HOURS);
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            var closing = closer.submit(jobs::close);
            // shutdown() rejects new work but must allow the running job to finish.
            while (!jobs.isShutdown()) Thread.onSpinWait();
            assertThat(closing.isDone()).isFalse();
            release.countDown();
            closing.get(5, TimeUnit.SECONDS);
            assertThat(completed).isTrue();
            assertThat(futureRan).isFalse();
            assertThat(jobs.isTerminated()).isTrue();
        } finally {
            release.countDown();
            jobs.shutdownNow();
            closer.shutdownNow();
        }
    }
}
