package demoday.backend.payment.domain;

import demoday.backend.payment.code.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(name = "product")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Product {

    private static final Pattern PRODUCT_NAME_PATTERN = Pattern.compile(
            "^[가-힣A-Za-z0-9 ()\\[\\]+\\-_=,./]+$"
    );

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_code", nullable = false, unique = true, length = 50)
    private String productCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(
            nullable = false,
            check = @CheckConstraint(
                    name = "ck_product_price_minimum",
                    constraint = "price >= 100"
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
        validateProduct(
                productCode,
                productType,
                name,
                price,
                fishAmount,
                passDurationHours,
                saleStartedAt,
                saleEndedAt,
                active
        );

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

    private static void validateProduct(
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
        if (productCode == null || productCode.isBlank()
                || productCode.length() > 50) {
            throw new IllegalArgumentException(
                    "상품 코드는 1자 이상 50자 이하여야 합니다."
            );
        }

        if (productType == null) {
            throw new IllegalArgumentException("상품 유형은 필수입니다.");
        }

        if (name == null || name.isBlank() || name.length() > 50
                || !PRODUCT_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "상품명은 코페이 허용 문자로 구성된 50자 이하여야 합니다."
            );
        }

        if (price == null || price < 100) {
            throw new IllegalArgumentException(
                    "결제 금액은 100원 이상이어야 합니다."
            );
        }

        if (productType == ProductType.FISH) {
            if (fishAmount == null || fishAmount <= 0) {
                throw new IllegalArgumentException(
                        "생선 상품의 지급 수량은 1 이상이어야 합니다."
                );
            }
            if (passDurationHours != null) {
                throw new IllegalArgumentException(
                        "생선 상품에는 패스 기간을 설정할 수 없습니다."
                );
            }
        }

        if (productType == ProductType.PASS) {
            if (passDurationHours == null || passDurationHours <= 0) {
                throw new IllegalArgumentException(
                        "패스 상품의 유효 시간은 1시간 이상이어야 합니다."
                );
            }
            if (fishAmount != null) {
                throw new IllegalArgumentException(
                        "패스 상품에는 생선 지급 수량을 설정할 수 없습니다."
                );
            }
        }

        if (saleStartedAt != null && saleEndedAt != null
                && !saleEndedAt.isAfter(saleStartedAt)) {
            throw new IllegalArgumentException(
                    "판매 종료 시각은 시작 시각보다 뒤여야 합니다."
            );
        }

        if (active == null) {
            throw new IllegalArgumentException("상품 활성 여부는 필수입니다.");
        }
    }

    public boolean isAvailableAt(LocalDateTime now) {
        if (!Boolean.TRUE.equals(active)) {
            return false;
        }

        if (saleStartedAt != null && now.isBefore(saleStartedAt)) {
            return false;
        }

        return saleEndedAt == null || now.isBefore(saleEndedAt);
    }
}
