package demoday.backend.ranking.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.ranking.dto.RankingPageResponse;
import demoday.backend.ranking.dto.StockRankingResponse;
import demoday.backend.ranking.dto.TimeAttackRankingResponse;
import demoday.backend.ranking.service.RankingQueryService;
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
    private final RankingController rankingController =
            new RankingController(rankingQueryService);

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
}
