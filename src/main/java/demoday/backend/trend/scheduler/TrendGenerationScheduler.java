package demoday.backend.trend.scheduler;

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

    @Scheduled(cron = "0 0 8 * * *", zone = "Asia/Seoul")
    public void generate() { run(1); }
    @Scheduled(cron = "0 5 8 * * *", zone = "Asia/Seoul")
    public void retryFirst() { run(2); }
    @Scheduled(cron = "0 15 8 * * *", zone = "Asia/Seoul")
    public void retrySecond() { run(3); }
    @Scheduled(cron = "0 30 8 * * *", zone = "Asia/Seoul")
    public void retryLast() { run(4); }

    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        try { generations.runLatestDueSlot(); }
        catch (RuntimeException exception) { log.error("경제 트렌드 시작 보완 실패. errorType={}", exception.getClass().getSimpleName()); }
    }
    private void run(int slot) {
        try { generations.runSlot(slot); }
        catch (RuntimeException exception) { log.error("경제 트렌드 실행 실패. slot={}, errorType={}", slot, exception.getClass().getSimpleName()); }
    }
}
