package demoday.backend.ranking.scheduler;

import demoday.backend.ranking.service.RankingSettlementService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RankingSettlementSchedulerTest {

    @Mock
    RankingSettlementService rankingSettlementService;

    private final java.time.LocalDate settlementStartDate =
            java.time.LocalDate.of(2026, 10, 31);

    private RankingSettlementScheduler scheduler() {
        return new RankingSettlementScheduler(
                rankingSettlementService,
                settlementStartDate.toString()
        );
    }

    @Test
    @DisplayName("자정 스케줄러가 미정산 날짜 처리를 실행한다")
    void settleAtMidnight() {
        scheduler().settleAtMidnight();

        verify(rankingSettlementService)
                .settlePendingDates(settlementStartDate);
    }

    @Test
    @DisplayName("서버가 시작될 때 미정산 날짜 처리를 실행한다")
    void settleOnStartup() {
        scheduler().settleOnStartup();

        verify(rankingSettlementService)
                .settlePendingDates(settlementStartDate);
    }

    @Test
    @DisplayName("랭킹 정산에 실패해도 예외를 스케줄러 밖으로 전파하지 않는다")
    void catchesSettlementFailure() {
        doThrow(new RuntimeException("정산 실패"))
                .when(rankingSettlementService)
                .settlePendingDates(settlementStartDate);

        assertThatCode(
                scheduler()::settleAtMidnight
        ).doesNotThrowAnyException();

        verify(rankingSettlementService)
                .settlePendingDates(settlementStartDate);
    }
}
