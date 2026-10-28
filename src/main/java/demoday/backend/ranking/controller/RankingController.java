package demoday.backend.ranking.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.ranking.dto.StockRankingResponse;
import demoday.backend.ranking.dto.TimeAttackRankingResponse;
import demoday.backend.ranking.dto.UnreadRankingRewardsResponse;
import demoday.backend.ranking.service.RankingQueryService;
import demoday.backend.ranking.service.RankingRewardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Ranking",
        description = "주가 및 시간외거래 랭킹 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/rankings")
public class RankingController {

    private final RankingQueryService rankingQueryService;
    private final RankingRewardService rankingRewardService;

    @Operation(
            summary = "주가 랭킹 조회",
            description = """
                    현재 누적 주가를 기준으로 랭킹을 조회합니다.
                    동점자는 동일한 순위를 가집니다.
                    스크롤을 위한 페이지 정보와 내 순위,
                    전체 회원 평균 주가를 함께 반환합니다.
                    """
    )
    @GetMapping("/stocks")
    public ApiResponse<StockRankingResponse>
    getStockRanking(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            Long memberId,

            @Parameter(
                    description = "페이지 번호, 0부터 시작",
                    example = "0"
            )
            @RequestParam(
                    defaultValue = "0"
            )
            int page,

            @Parameter(
                    description = "페이지당 조회 개수, 최대 100",
                    example = "20"
            )
            @RequestParam(
                    defaultValue = "20"
            )
            int size
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                rankingQueryService.getStockRanking(
                        memberId,
                        page,
                        size
                )
        );
    }

    @Operation(
            summary = "시간외거래 랭킹 조회",
            description = """
                    KST 기준 오늘 완료된 시간외거래의
                    회원별 최고 정답 기록을 기준으로 조회합니다.
                    동점자는 동일한 순위를 가집니다.
                    당일 미참여 회원의 내 랭킹은 null로 반환합니다.
                    """
    )
    @GetMapping("/time-attacks")
    public ApiResponse<TimeAttackRankingResponse>
    getTimeAttackRanking(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            Long memberId,

            @Parameter(
                    description = "페이지 번호, 0부터 시작",
                    example = "0"
            )
            @RequestParam(
                    defaultValue = "0"
            )
            int page,

            @Parameter(
                    description = "페이지당 조회 개수, 최대 100",
                    example = "20"
            )
            @RequestParam(
                    defaultValue = "20"
            )
            int size
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                rankingQueryService
                        .getTimeAttackRanking(
                                memberId,
                                page,
                                size
                        )
        );
    }

    @Operation(
            summary = "미확인 랭킹 보상 조회",
            description = """
                아직 확인하지 않은 주가 및 시간외거래 랭킹 보상을 조회합니다.
                두 랭킹 모두 보상 대상이면 보상 두 건을 반환합니다.
                패스 여부와 관계없이 FISH 지급 전후 잔액을 반환합니다.
                """
    )
    @GetMapping("/rewards/unread")
    public ApiResponse<UnreadRankingRewardsResponse>
    getUnreadRankingRewards(
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                rankingRewardService.getUnreadRewards(memberId)
        );
    }

    @Operation(
            summary = "랭킹 보상 확인 처리",
            description = """
                팝업 확인이 끝난 랭킹 보상을 확인 처리합니다.
                같은 요청을 여러 번 호출해도 성공합니다.
                """
    )
    @PatchMapping("/rewards/{rankingRewardId}/read")
    public ApiResponse<Void> readRankingReward(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long rankingRewardId
    ) {
        rankingRewardService.markAsRead(
                memberId,
                rankingRewardId
        );

        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK
        );
    }
}
