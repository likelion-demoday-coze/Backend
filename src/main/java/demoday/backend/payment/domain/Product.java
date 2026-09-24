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

    @Column(nullable = false)
    private Integer price;

    @Column(name = "fish_amount")
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
}
