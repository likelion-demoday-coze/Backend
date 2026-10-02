package demoday.backend.store.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.store.dto.StoreItemResponse;
import demoday.backend.store.service.StoreService;
import demoday.backend.store.service.StorePurchaseService;
import demoday.backend.store.dto.StorePurchaseRequest;
import demoday.backend.store.dto.StorePurchaseResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final StorePurchaseService storePurchaseService;

    @Operation(summary = "상점 아이템 구매", description = "세션 인증과 CSRF 토큰이 필요합니다. 서버 가격으로 생선을 차감하고 보유 수량을 증가시킵니다. 같은 requestId와 상품·수량으로 재요청하면 최초 구매 결과를 반환합니다. 다른 상품·수량으로 재사용하면 409입니다. 패스 회원도 실제 생선을 사용합니다.")
    @PostMapping("/{itemId}/purchase")
    public ApiResponse<StorePurchaseResponse> purchase(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId,
            @PathVariable Long itemId, @Valid @RequestBody StorePurchaseRequest request
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, storePurchaseService.purchase(memberId, itemId, request));
    }

    @Operation(summary = "판매 중인 상점 아이템 조회", description = "세션 인증이 필요합니다. 활성 아이템을 ID 오름차순으로 반환하며 판매 중인 아이템이 없으면 빈 목록을 반환합니다. 가격은 아이템 1개당 생선 수량입니다.")
    @GetMapping
    public ApiResponse<List<StoreItemResponse>> getItems(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, storeService.getItems(memberId));
    }
}
