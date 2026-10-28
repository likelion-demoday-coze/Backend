package demoday.backend.ranking.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.ranking.dto.RankingPageResponse;
import demoday.backend.ranking.dto.StockRankingResponse;
import demoday.backend.ranking.dto.TimeAttackRankingResponse;
import demoday.backend.ranking.dto.UnreadRankingRewardsResponse;
import demoday.backend.ranking.service.RankingQueryService;
import demoday.backend.ranking.service.RankingRewardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RankingControllerTest {

    private final RankingQueryService rankingQueryService =
            mock(RankingQueryService.class);
    private final RankingRewardService rankingRewardService =
            mock(RankingRewardService.class);
    private final RankingController rankingController =
            new RankingController(
                    rankingQueryService,
                    rankingRewardService
            );

    @Test
    @DisplayName("주가 랭킹 조회 결과를 공통 성공 응답으로 반환한다")
    void getStockRanking() {
        StockRankingResponse expected = new StockRankingResponse(
                LocalDateTime.of(2026, 10, 3, 12, 0),
                0,
                BigDecimal.ZERO,
                List.of(),
                null,
                new RankingPageResponse(1, 20, false)
        );
        when(rankingQueryService.getStockRanking(1L, 1, 20))
                .thenReturn(expected);

        ApiResponse<StockRankingResponse> response =
                rankingController.getStockRanking(1L, 1, 20);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isSameAs(expected);
        verify(rankingQueryService).getStockRanking(1L, 1, 20);
    }

    @Test
    @DisplayName("타임어택 랭킹 조회 결과를 공통 성공 응답으로 반환한다")
    void getTimeAttackRanking() {
        TimeAttackRankingResponse expected = new TimeAttackRankingResponse(
                LocalDate.of(2026, 10, 3),
                0,
                BigDecimal.ZERO,
                List.of(),
                null,
                new RankingPageResponse(0, 20, false)
        );
        when(rankingQueryService.getTimeAttackRanking(1L, 0, 20))
                .thenReturn(expected);

        ApiResponse<TimeAttackRankingResponse> response =
                rankingController.getTimeAttackRanking(1L, 0, 20);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isSameAs(expected);
        verify(rankingQueryService).getTimeAttackRanking(1L, 0, 20);
    }

    @Test
    @DisplayName("미확인 랭킹 보상 조회 결과를 공통 성공 응답으로 반환한다")
    void getUnreadRankingRewards() {
        UnreadRankingRewardsResponse expected =
                new UnreadRankingRewardsResponse(List.of());
        when(rankingRewardService.getUnreadRewards(1L))
                .thenReturn(expected);

        ApiResponse<UnreadRankingRewardsResponse> response =
                rankingController.getUnreadRankingRewards(1L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isSameAs(expected);
        verify(rankingRewardService).getUnreadRewards(1L);
    }

    @Test
    @DisplayName("랭킹 보상 확인 요청을 서비스에 위임한다")
    void readRankingReward() {
        ApiResponse<Void> response =
                rankingController.readRankingReward(1L, 10L);

        assertThat(response.isSuccess()).isTrue();
        verify(rankingRewardService).markAsRead(1L, 10L);
    }
}
