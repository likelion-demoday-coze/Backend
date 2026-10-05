package demoday.backend.trend.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.trend.dto.TodayTrendsResponse;
import demoday.backend.trend.dto.TrendDetailResponse;
import demoday.backend.trend.service.EconomicTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Economic Trend", description = "저장된 경제 트렌드 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/economic-trends")
public class EconomicTrendController {
    private final EconomicTrendService economicTrendService;

    @Operation(summary = "오늘의 경제 트렌드 3개 조회", description = "세션 인증과 ROLE_MEMBER가 필요합니다. KST 오늘의 성공 콘텐츠 또는 최근 72시간 이내의 마지막 성공 콘텐츠를 제공합니다. 사용할 콘텐츠가 없으면 200과 PREPARING 상태 및 빈 items를 반환합니다. 조회는 외부 API를 호출하지 않습니다.")
    @GetMapping("/today")
    public ApiResponse<TodayTrendsResponse> getToday(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, economicTrendService.getToday(memberId));
    }

    @Operation(summary = "경제 트렌드 상세 조회", description = "세션 인증과 ROLE_MEMBER가 필요합니다. 최근 72시간 이내의 성공한 3개 콘텐츠 묶음에 속한 트렌드의 상세 요약·경제 용어·출처와 생성 기준 날짜를 반환합니다. 목록 조회 후 새 콘텐츠가 생성되어도 기존 콘텐츠가 제공 조건을 만족하면 조회할 수 있습니다. 없는 ID나 제공할 수 없는 콘텐츠는 404, 0 이하 ID는 400입니다. 조회는 외부 API를 호출하지 않습니다.")
    @GetMapping("/{trendId}")
    public ApiResponse<TrendDetailResponse> getDetail(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long trendId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, economicTrendService.getDetail(memberId, trendId));
    }
}
