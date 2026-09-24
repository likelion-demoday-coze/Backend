package demoday.backend.stock.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.stock.code.StockChangeType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "stock_change")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StockChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_change_id")
    private Long stockChangeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private StockChangeType changeType;

    @Column(name = "stock_before", nullable = false, precision = 30, scale = 2)
    private BigDecimal stockBefore;

    @Column(name = "stock_after", nullable = false, precision = 30, scale = 2)
    private BigDecimal stockAfter;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static StockChange create(
            Member member,
            StockChangeType changeType,
            BigDecimal stockBefore,
            BigDecimal stockAfter,
            Long referenceId,
            String idempotencyKey,
            LocalDateTime createdAt
    ) {
        return StockChange.builder()
                .member(member)
                .changeType(changeType)
                .stockBefore(stockBefore)
                .stockAfter(stockAfter)
                .referenceId(referenceId)
                .idempotencyKey(idempotencyKey)
                .createdAt(createdAt)
                .build();
    }
}
