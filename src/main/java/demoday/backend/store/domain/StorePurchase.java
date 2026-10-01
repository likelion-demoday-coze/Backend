package demoday.backend.store.domain;

import demoday.backend.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/** 구매 당시 가격과 결과를 보존해 재요청에 현재 가격을 적용하지 않는다. */
@Getter
@Entity
@Table(name = "store_purchase", uniqueConstraints = @UniqueConstraint(
        name = "uk_store_purchase_member_request", columnNames = {"member_id", "request_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StorePurchase {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long purchaseId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private StoreItem item;
    @Column(name = "request_id", nullable = false, length = 36)
    private String requestId;
    @Column(nullable = false) private Integer quantity;
    @Column(nullable = false) private Integer unitPrice;
    @Column(nullable = false) private Long totalPrice;
    @Column(nullable = false) private Long balanceAfter;
    @Column(nullable = false) private Integer quantityAfter;
    @Column(nullable = false) private LocalDateTime createdAt;

    public static StorePurchase create(Member member, StoreItem item, String requestId, int quantity,
            long totalPrice, long balanceAfter, int quantityAfter, LocalDateTime createdAt) {
        StorePurchase purchase = new StorePurchase();
        purchase.member = member;
        purchase.item = item;
        purchase.requestId = requestId;
        purchase.quantity = quantity;
        purchase.unitPrice = item.getFishPrice();
        purchase.totalPrice = totalPrice;
        purchase.balanceAfter = balanceAfter;
        purchase.quantityAfter = quantityAfter;
        purchase.createdAt = createdAt;
        return purchase;
    }
}
