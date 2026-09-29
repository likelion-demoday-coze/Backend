package demoday.backend.stock.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.stock.dto.StockResponse;
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

    @Operation(summary = "내 현재 주가 조회", description = "로그인한 회원의 현재 주가를 반환합니다. 탈퇴한 회원은 이용할 수 없습니다.")
    @GetMapping("/me")
    public ApiResponse<StockResponse> getCurrentStock(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, stockService.getCurrentStock(memberId));
    }
}
