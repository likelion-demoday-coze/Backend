package demoday.backend.fish.controller;

import demoday.backend.fish.dto.FishBalanceResponse;
import demoday.backend.fish.dto.FishTransactionPageResponse;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Fish", description = "게임 내 재화(생선) 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/fish/me")
public class FishController {

    private final FishService fishService;

    @Operation(summary = "내 생선 잔액 조회", description = "패스 여부와 관계없이 실제 보유 잔액을 반환합니다.")
    @GetMapping("/balance")
    public ApiResponse<FishBalanceResponse> getBalance(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, fishService.getBalance(memberId));
    }

    @Operation(summary = "내 생선 증감 내역 조회",
            description = "본인의 거래만 발생 시각 내림차순으로 반환합니다. 같은 시각이면 거래 ID 내림차순입니다.")
    @GetMapping("/transactions")
    public ApiResponse<FishTransactionPageResponse> getTransactions(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @Parameter(description = "0부터 시작하는 페이지 번호")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기 (1~100, 기본 20)")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK, fishService.getTransactions(memberId, page, size)
        );
    }
}
