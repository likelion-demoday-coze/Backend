package demoday.backend.trend.scheduler;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import demoday.backend.global.logging.JobRunContext;
import demoday.backend.trend.service.TrendGenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.economic-trends.enabled", havingValue = "true")
public class TrendGenerationScheduler {

    private final TrendGenerationService generations;
    private final JobRunContext jobRunContext;

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "economic-trend-generation", lockAtLeastFor = "${app.scheduling.lock-at-least-for:1m}")
    public void generate() {
        executeSlot("economic-trend-slot-1", 1);
    }

    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "economic-trend-generation", lockAtLeastFor = "${app.scheduling.lock-at-least-for:1m}")
    public void retryFirst() {
        executeSlot("economic-trend-slot-2", 2);
    }

    @Scheduled(cron = "0 15 8 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "economic-trend-generation", lockAtLeastFor = "${app.scheduling.lock-at-least-for:1m}")
    public void retrySecond() {
        executeSlot("economic-trend-slot-3", 3);
    }

    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "economic-trend-generation", lockAtLeastFor = "${app.scheduling.lock-at-least-for:1m}")
    public void retryLast() {
        executeSlot("economic-trend-slot-4", 4);
    }

    @EventListener(ApplicationReadyEvent.class)
    @SchedulerLock(name = "economic-trend-generation", lockAtLeastFor = "${app.scheduling.lock-at-least-for:1m}")
    public void catchUp() {
        executeSafely(
                "economic-trend-startup-catch-up",
                generations::runLatestDueSlot
        );
    }

    private void executeSlot(
            String jobName,
            int slot
    ) {
        executeSafely(
                jobName,
                () -> generations.runSlot(slot)
        );
    }

    private void executeSafely(
            String jobName,
            Runnable task
    ) {
        try {
            jobRunContext.run(jobName, task);
        } catch (RuntimeException exception) {
            // JobRunContext가 jobRunId와 함께 실패 스택 트레이스를 기록
            // 스케줄러와 ApplicationReady 이벤트 밖으로는 전파하지 않음
            log.warn(
                    "[Scheduler] 경제 트렌드 작업 실패를 격리했습니다. jobName: {}, errorType: {}",
                    jobName,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
