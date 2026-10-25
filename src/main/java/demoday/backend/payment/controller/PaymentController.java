package demoday.backend.payment.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.payment.dto.*;
import demoday.backend.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment", description = "결제 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentQueryService paymentQueryService;

    @Operation(
            summary = "내 결제 상태 조회",
            description = """
                로그인 회원이 주문번호로 자신의 결제 상태를 조회합니다.
                READY는 결제 요청 전, APPROVED는 승인 완료 후 상품 지급 대기,
                COMPLETED는 상품 지급까지 완료된 상태입니다.
                다른 회원의 주문은 조회할 수 없습니다.
                """
    )
    @GetMapping("/orders/{orderNumber}")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(
                    description = "결제 주문번호",
                    example = "PAY20261006123000A1B2C3D4E5F6"
            )
            @PathVariable String orderNumber
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentQueryService.getStatus(
                        memberId,
                        orderNumber
                )
        );
    }

    @Operation(summary = "내 결제 내역 목록 조회")
    @GetMapping
    public ApiResponse<PaymentHistoryPageResponse> getPaymentHistories(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentQueryService.getHistories(memberId, pageable)
        );
    }

    @Operation(summary = "내 결제 내역 상세 조회")
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentStatusResponse> getPaymentDetail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PathVariable Long paymentId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentQueryService.getDetail(memberId, paymentId)
        );
    }
}
