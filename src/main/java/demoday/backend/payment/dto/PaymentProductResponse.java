package demoday.backend.payment.dto;

import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.domain.Product;
import io.swagger.v3.oas.annotations.media.Schema;

public record PaymentProductResponse(

        @Schema(description = "상품 ID", example = "1")
        Long productId,

        @Schema(description = "상품 코드", example = "WEEKLY_PASS")
        String productCode,

        @Schema(description = "상품 유형", example = "PASS")
        ProductType productType,

        @Schema(description = "상품명", example = "일주일 패스")
        String name,

        @Schema(description = "가격", example = "1900")
        Integer price,

        @Schema(description = "패스 유효 시간", nullable = true, example = "168")
        Integer passDurationHours
) {

    public static PaymentProductResponse from(Product product) {
        return new PaymentProductResponse(
                product.getProductId(),
                product.getProductCode(),
                product.getProductType(),
                product.getName(),
                product.getPrice(),
                product.getPassDurationHours()
        );
    }
}
