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
        jobRunContext.run(
                "ranking-settlement-startup",
                () -> settlePendingRankings("서버 시작")
        );
    }

    @Scheduled(
            cron = "0 0 0 * * *",
            zone = "Asia/Seoul"
    )
    public void settleAtMidnight() {
        jobRunContext.run(
                "ranking-settlement-midnight",
                () -> settlePendingRankings("자정 스케줄")
        );
    }

    private void settlePendingRankings(String trigger) {
        log.info("랭킹 미정산 날짜 처리를 시작합니다. trigger={}", trigger);

        try {
            rankingSettlementService.settlePendingDates(
                    settlementStartDate
            );
            log.info("랭킹 미정산 날짜 처리를 완료했습니다. trigger={}", trigger);
        } catch (Exception exception) {
            log.error(
                    "랭킹 미정산 날짜 처리 중 오류가 발생했습니다. trigger={}",
                    trigger,
                    exception
            );
        }
    }
}
