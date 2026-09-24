package demoday.backend.payment.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.payment.code.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "payment")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "order_id", nullable = false, unique = true, length = 100)
    private String orderId;

    // PG사 정책에 따라 수정 가능
    @Column(name = "pg_payment_id", unique = true, length = 150)
    private String pgPaymentId;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public static Payment create(
            Member member,
            Product product,
            String orderId,
            Integer amount,
            PaymentStatus status,
            LocalDateTime requestedAt
    ) {
        return Payment.builder()
                .member(member)
                .product(product)
                .orderId(orderId)
                .amount(amount)
                .status(status)
                .requestedAt(requestedAt)
                .build();
    }
}
