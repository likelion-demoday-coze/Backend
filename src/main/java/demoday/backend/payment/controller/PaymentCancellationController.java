package demoday.backend.payment.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.payment.dto.PaymentCancelResponse;
import demoday.backend.payment.service.PaymentCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment cancel", description = "결제 취소 조회 API")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "app.payment",
        name = "enabled",
        havingValue = "true"
)
public class PaymentCancellationController {

    private final PaymentCancellationService cancellationService;

    @Operation(
            summary = "결제 전체 취소",
            description = "승인 후 지급 대기 결제 또는 아직 유효한 패스 결제를 전액 취소합니다. 환불 성공 시 패스는 즉시 취소됩니다."
    )
    @PostMapping("/{paymentId}/cancel")
    public ApiResponse<PaymentCancelResponse> cancelPayment(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long paymentId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                cancellationService.cancel(memberId, paymentId)
        );
    }
}
