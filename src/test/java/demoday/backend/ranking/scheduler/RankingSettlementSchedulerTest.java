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

    @InjectMocks
    RankingSettlementScheduler rankingSettlementScheduler;

    @Test
    @DisplayName("스케줄러가 전날 랭킹 정산을 실행한다")
    void settlePreviousDayRanking() {
        rankingSettlementScheduler.settlePreviousDayRanking();

        verify(rankingSettlementService).settlePreviousDay();
    }

    @Test
    @DisplayName("랭킹 정산에 실패해도 예외를 스케줄러 밖으로 전파하지 않는다")
    void catchesSettlementFailure() {
        doThrow(new RuntimeException("정산 실패"))
                .when(rankingSettlementService)
                .settlePreviousDay();

        assertThatCode(
                rankingSettlementScheduler::settlePreviousDayRanking
        ).doesNotThrowAnyException();

        verify(rankingSettlementService).settlePreviousDay();
    }
}
