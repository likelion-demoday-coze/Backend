package demoday.backend.payment.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.payment.dto.PaymentOrderRequest;
import demoday.backend.payment.dto.PaymentOrderResponse;
import demoday.backend.payment.service.PaymentOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Payment Order", description = "결제 주문 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments/orders")
@ConditionalOnProperty(
        prefix = "app.payment",
        name = "enabled",
        havingValue = "true"
)
public class PaymentOrderController {

    private final PaymentOrderService paymentOrderService;

    @Operation(summary = "결제 주문 생성")
    @PostMapping
    public ApiResponse<PaymentOrderResponse> createOrder(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Valid @RequestBody PaymentOrderRequest request
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentOrderService.createOrder(
                        memberId,
                        request
                )
        );
    }
}
