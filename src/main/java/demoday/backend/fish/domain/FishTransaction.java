package demoday.backend.fish.domain;

import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "fish_transaction")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class FishTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fish_transaction_id")
    private Long fishTransactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private FishTransactionType transactionType;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static FishTransaction create(
            Member member,
            FishTransactionType transactionType,
            Long amount,
            Long balanceAfter,
            Long referenceId,
            String idempotencyKey,
            LocalDateTime createdAt
    ) {
        return FishTransaction.builder()
                .member(member)
                .transactionType(transactionType)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .referenceId(referenceId)
                .idempotencyKey(idempotencyKey)
                .createdAt(createdAt)
                .build();
    }
}
