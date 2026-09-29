package demoday.backend.stock.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.stock.dto.StockResponse;
import demoday.backend.stock.dto.StockChangePageResponse;
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

    @Operation(summary = "내 주가 변동 내역 조회",
            description = "본인의 변동 내역을 발생 시각 내림차순으로 반환합니다. 같은 시각이면 변동 ID 내림차순입니다. 내역이 없으면 빈 목록을 반환합니다.")
    @GetMapping("/me/changes")
    public ApiResponse<StockChangePageResponse> getChanges(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "0부터 시작하는 페이지 번호")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100, 기본 20)")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, stockService.getChanges(memberId, page, size));
    }

    @Operation(summary = "내 현재 주가 조회", description = "로그인한 회원의 현재 주가를 반환합니다. 탈퇴한 회원은 이용할 수 없습니다.")
    @GetMapping("/me")
    public ApiResponse<StockResponse> getCurrentStock(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, stockService.getCurrentStock(memberId));
    }
}
