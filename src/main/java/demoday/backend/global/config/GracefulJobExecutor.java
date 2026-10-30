package demoday.backend.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Drain running jobs before the context destroys JPA, the datasource or lock heartbeats. */
@Slf4j
public class GracefulJobExecutor extends ScheduledThreadPoolExecutor
        implements ApplicationListener<ContextClosedEvent>, AutoCloseable {
    public GracefulJobExecutor() {
        super(1, runnable -> new Thread(runnable, "scheduled-job"));
        setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        setRemoveOnCancelPolicy(true);
    }

    @Override
    public void onApplicationEvent(ContextClosedEvent event) {
        close();
    }

    @Override
    public void close() {
        shutdown();
        try {
            if (!awaitTermination(1800, TimeUnit.SECONDS)) {
                log.error("Scheduler shutdown exceeded 30 minutes; interrupting remaining jobs");
                shutdownNow();
            }
        } catch (InterruptedException exception) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
