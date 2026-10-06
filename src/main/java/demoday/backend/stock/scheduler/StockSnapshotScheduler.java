package demoday.backend.stock.scheduler;

import demoday.backend.stock.service.StockSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
public class StockSnapshotScheduler {
    private final StockSnapshotService snapshots;
    private final Clock clock;
    private final LocalDate startDate;

    public StockSnapshotScheduler(StockSnapshotService snapshots, Clock clock,
            @Value("${app.stock.snapshot-start-date:${app.ranking.settlement-start-date:2026-10-31}}") String startDate) {
        this.snapshots = snapshots;
        this.clock = clock;
        this.startDate = LocalDate.parse(startDate);
    }
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void captureAtMidnight() { capturePendingDates(); }

    @EventListener(ApplicationReadyEvent.class)
    public void captureOnStartup() { capturePendingDates(); }

    /** 일부 회원만 저장된 날짜도 공통 서비스가 보완하며, 날짜별 실패를 분리한다. */
    public void capturePendingDates() {
        LocalDate yesterday = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))).minusDays(1);
        for (LocalDate date = startDate; !date.isAfter(yesterday); date = date.plusDays(1)) {
            try { snapshots.capture(date); }
            catch (RuntimeException exception) {
                log.error("주가 스냅샷 저장 실패. snapshotDate={}", date, exception);
            }
        }
    }
}
