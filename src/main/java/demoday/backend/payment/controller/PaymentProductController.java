package demoday.backend.payment.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.payment.dto.PaymentProductResponse;
import demoday.backend.payment.service.PaymentProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Payment Product", description = "결제 상품 조회 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products")
public class PaymentProductController {

    private final PaymentProductService paymentProductService;

    @Operation(summary = "판매 중인 결제 상품 목록 조회")
    @GetMapping
    public ApiResponse<List<PaymentProductResponse>> getProducts() {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentProductService.getAvailableProducts()
        );
    }

    @Operation(summary = "결제 상품 상세 조회")
    @GetMapping("/{productId}")
    public ApiResponse<PaymentProductResponse> getProduct(
            @Parameter(description = "상품 ID", example = "1")
            @PathVariable Long productId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentProductService.getAvailableProduct(productId)
        );
    }
}
