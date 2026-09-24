package demoday.backend.payment.domain;

import demoday.backend.payment.code.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "product")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_code", nullable = false, unique = true, length = 50)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(
            nullable = false,
            check = @CheckConstraint(
                    name = "ck_product_price_non_negative",
                    constraint = "price >= 0"
            )
    )
    private Integer price;

    @Column(
            name = "fish_amount",
            check = @CheckConstraint(
                    name = "ck_product_fish_amount_non_negative",
                    constraint = "fish_amount IS NULL OR fish_amount >= 0"
            )
    )
    private Integer fishAmount;

    @Column(name = "pass_duration_hours")
    private Integer passDurationHours;

    @Column(name = "sale_started_at")
    private LocalDateTime saleStartedAt;

    @Column(name = "sale_ended_at")
    private LocalDateTime saleEndedAt;

    @Column(nullable = false)
    private Boolean active;

    public static Product create(
            String productCode,
            ProductType productType,
            String name,
            Integer price,
            Integer fishAmount,
            Integer passDurationHours,
            LocalDateTime saleStartedAt,
            LocalDateTime saleEndedAt,
            Boolean active
    ) {
        validateAmount(price, fishAmount);

        return Product.builder()
                .productCode(productCode)
                .productType(productType)
                .name(name)
                .price(price)
                .fishAmount(fishAmount)
                .passDurationHours(passDurationHours)
                .saleStartedAt(saleStartedAt)
                .saleEndedAt(saleEndedAt)
                .active(active)
                .build();
    }

    private static void validateAmount(Integer price, Integer fishAmount) {
        if (price == null || price < 0) {
            throw new IllegalArgumentException(
                    "상품 가격은 0 이상이어야 합니다."
            );
        }

        if (fishAmount != null && fishAmount < 0) {
            throw new IllegalArgumentException(
                    "지급 생선 수량은 0 이상이어야 합니다."
            );
        }
    }
}
