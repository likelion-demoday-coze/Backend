package demoday.backend.ranking.scheduler;

import demoday.backend.global.logging.JobRunContext;
import demoday.backend.ranking.service.RankingSettlementService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Slf4j
@Component
public class RankingSettlementScheduler {

    private final RankingSettlementService rankingSettlementService;
    private final LocalDate settlementStartDate;
    private final JobRunContext jobRunContext;

    public RankingSettlementScheduler(
            RankingSettlementService rankingSettlementService,
            JobRunContext jobRunContext,
            @Value("${app.ranking.settlement-start-date:2026-10-31}")
            String settlementStartDate
    ) {
        this.rankingSettlementService = rankingSettlementService;
        this.jobRunContext = jobRunContext;
        this.settlementStartDate = LocalDate.parse(settlementStartDate);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void settleOnStartup() {
        runSafely(
                "ranking-settlement-startup",
                "서버 시작"
        );
    }

    @Scheduled(
            cron = "0 0 0 * * *",
            zone = "Asia/Seoul"
    )
    public void settleAtMidnight() {
        runSafely(
                "ranking-settlement-midnight",
                "자정 스케줄"
        );
    }

    private void settlePendingRankings(String trigger) {
        log.info(
                "랭킹 미정산 날짜 처리를 시작합니다. trigger={}",
                trigger
        );

        rankingSettlementService.settlePendingDates(
                settlementStartDate
        );

        log.info(
                "랭킹 미정산 날짜 처리를 완료했습니다. trigger={}",
                trigger
        );
    }

    private void runSafely(
            String jobName,
            String trigger
    ) {
        try {
            jobRunContext.run(
                    jobName,
                    () -> settlePendingRankings(trigger)
            );
        } catch (RuntimeException exception) {
            // JobRunContext에서 실패 원인과 스택 트레이스를 이미 기록함
            // 스케줄러 및 ApplicationReady 이벤트 밖으로는 전파하지 않음
            log.warn(
                    "[Scheduler] 랭킹 정산 실패를 격리했습니다. trigger: {}, errorType: {}",
                    trigger,
                    exception.getClass().getSimpleName()
            );
        }
    }
}
