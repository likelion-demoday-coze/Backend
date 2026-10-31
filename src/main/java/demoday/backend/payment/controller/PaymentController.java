package demoday.backend.payment.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.payment.dto.*;
import demoday.backend.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

    @Operation(
            summary = "내 결제 내역 목록 조회",
            description = "결제 완료, 지급 대기, 취소 처리 중, 환불 완료 내역을 조회합니다."
    )
    @GetMapping
    public ApiResponse<PaymentHistoryPageResponse> getPaymentHistories(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(description = "0부터 시작하는 페이지 번호")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "페이지 크기 (1~100, 기본 20)")
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentQueryService.getHistories(memberId, page, size)
        );
    }

    @Operation(
            summary = "내 결제 내역 상세 조회",
            description = """
                    결제 정보와 패스 이용 기간, 영수증 URL, 환불 처리 상태를 조회합니다.
                    refundable이 true인 경우에만 환불 신청 버튼을 노출합니다.
                    다른 회원의 결제 내역은 조회할 수 없습니다.
                    """
    )
    @GetMapping("/{paymentId}")
    public ApiResponse<PaymentDetailResponse> getPaymentDetail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(description = "결제 ID", example = "1")
            @PathVariable Long paymentId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentQueryService.getDetail(memberId, paymentId)
        );
    }
}
