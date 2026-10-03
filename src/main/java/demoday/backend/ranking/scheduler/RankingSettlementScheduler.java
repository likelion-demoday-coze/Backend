package demoday.backend.ranking.scheduler;

import demoday.backend.ranking.service.RankingSettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RankingSettlementScheduler {

    private final RankingSettlementService rankingSettlementService;

    // 매일 00:00 KST에 전날 랭킹을 확정하고 보상을 지급
    // 주가 랭킹은 스트릭 미학습에 따른 20% 하락이 적용되기 전에 조회
    @Scheduled(
            cron = "0 0 0 * * *",
            zone = "Asia/Seoul"
    )
    public void settlePreviousDayRanking() {
        log.info("전날 랭킹 정산을 시작합니다.");

        try {
            rankingSettlementService.settlePreviousDay();
            log.info("전날 랭킹 정산을 완료했습니다.");
        } catch (Exception exception) {
            log.error(
                    "전날 랭킹 정산 중 오류가 발생했습니다.",
                    exception
            );
        }
    }
}
