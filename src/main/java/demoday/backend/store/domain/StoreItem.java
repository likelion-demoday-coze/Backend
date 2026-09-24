package demoday.backend.store.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(name = "store_item")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StoreItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @Column(name = "item_code", nullable = false, unique = true, length = 50)
    private String itemCode;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(
            name = "fish_price",
            nullable = false,
            check = @CheckConstraint(
                    name = "ck_store_item_fish_price_non_negative",
                    constraint = "fish_price >= 0"
            )
    )
    private Integer fishPrice;

    @Column(nullable = false)
    private Boolean active;

    public static StoreItem create(
            String itemCode,
            String name,
            Integer fishPrice,
            Boolean active
    ) {
        validateFishPrice(fishPrice);

        return StoreItem.builder()
                .itemCode(itemCode)
                .name(name)
                .fishPrice(fishPrice)
                .active(active)
                .build();
    }

    private static void validateFishPrice(Integer fishPrice) {
        if (fishPrice == null || fishPrice < 0) {
            throw new IllegalArgumentException(
                    "생선 판매 가격은 0 이상이어야 합니다."
            );
        }
    }
}
