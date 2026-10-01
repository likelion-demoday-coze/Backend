package demoday.backend.store.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.store.dto.StoreItemResponse;
import demoday.backend.store.service.StoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Store", description = "생선으로 구매하는 상점 아이템")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/store/items")
public class StoreController {
    private final StoreService storeService;

    @Operation(summary = "판매 중인 상점 아이템 조회", description = "세션 인증이 필요합니다. 활성 아이템을 ID 오름차순으로 반환하며 판매 중인 아이템이 없으면 빈 목록을 반환합니다. 가격은 아이템 1개당 생선 수량입니다.")
    @GetMapping
    public ApiResponse<List<StoreItemResponse>> getItems(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, storeService.getItems(memberId));
    }
}
