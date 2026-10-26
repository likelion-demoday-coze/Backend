package demoday.backend.global.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class JobRunContext {

    private static final String JOB_RUN_ID_KEY = "jobRunId";
    private static final String JOB_NAME_KEY = "jobName";

    public void run(
            String jobName,
            Runnable task
    ) {
        String previousJobRunId = MDC.get(JOB_RUN_ID_KEY);
        String previousJobName = MDC.get(JOB_NAME_KEY);

        String jobRunId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();

        MDC.put(JOB_RUN_ID_KEY, jobRunId);
        MDC.put(JOB_NAME_KEY, jobName);

        log.info(
                "[Scheduler] 작업 시작 - jobName: {}, jobRunId: {}",
                jobName,
                jobRunId
        );

        try {
            task.run();

            long durationMillis =
                    (System.nanoTime() - startedAt) / 1_000_000;

            log.info(
                    "[Scheduler] 작업 완료 - jobName: {}, jobRunId: {}, durationMs: {}",
                    jobName,
                    jobRunId,
                    durationMillis
            );
        } catch (RuntimeException exception) {
            long durationMillis =
                    (System.nanoTime() - startedAt) / 1_000_000;

            log.error(
                    "[Scheduler] 작업 실패 - jobName: {}, jobRunId: {}, durationMs: {}, errorType: {}",
                    jobName,
                    jobRunId,
                    durationMillis,
                    exception.getClass().getSimpleName(),
                    exception
            );

            throw exception;
        } finally {
            restore(JOB_RUN_ID_KEY, previousJobRunId);
            restore(JOB_NAME_KEY, previousJobName);
        }
    }

    private void restore(
            String key,
            String previousValue
    ) {
        if (previousValue == null) {
            MDC.remove(key);
            return;
        }

        MDC.put(key, previousValue);
    }
}
