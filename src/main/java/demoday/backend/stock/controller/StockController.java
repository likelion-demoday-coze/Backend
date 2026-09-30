package demoday.backend.stock.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.stock.dto.StockResponse;
import demoday.backend.stock.dto.StockGraphResponse;
import demoday.backend.stock.code.StockGraphPeriod;
import demoday.backend.stock.dto.StockHistoryResponse;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.RequestParam;
import demoday.backend.stock.service.StockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Stock", description = "내 주가 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stocks")
public class StockController {

    private final StockService stockService;

    @Operation(summary = "기간별 주가 그래프 조회",
            description = "DAY는 KST 오늘, WEEK는 오늘 포함 7일의 변동 이력입니다. ALL은 가입 이후 일별 스냅샷이며 기간 제한이 없습니다. 시작값과 현재값을 포함합니다. 가입 시각이 없는 기존 회원은 최초 기록 기준이며 estimatedStart=true입니다.")
    @GetMapping("/me/graph")
    public ApiResponse<StockGraphResponse> getGraph(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @RequestParam(defaultValue = "ALL") StockGraphPeriod period
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, stockService.getGraph(memberId, period));
    }

    @Operation(summary = "내 일별 주가 그래프 조회",
            description = "저장된 일별 스냅샷을 날짜 오름차순으로 반환합니다. from/to는 yyyy-MM-dd 형식으로 함께 지정하며 양 끝 날짜를 포함해 최대 366일입니다. 둘 다 생략하면 KST 오늘을 포함한 최근 30일입니다. 저장되지 않은 날짜는 제외하며 실시간 주가를 덧붙이지 않습니다.")
    @GetMapping("/me/history")
    public ApiResponse<StockHistoryResponse> getHistory(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "조회 시작일 (종료일과 함께 지정)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "조회 종료일 (시작일과 함께 지정)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, stockService.getHistory(memberId, from, to));
    }

    @Operation(summary = "내 현재 주가 조회", description = "로그인한 회원의 현재 주가를 반환합니다. 탈퇴한 회원은 이용할 수 없습니다.")
    @GetMapping("/me")
    public ApiResponse<StockResponse> getCurrentStock(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, stockService.getCurrentStock(memberId));
    }
}
