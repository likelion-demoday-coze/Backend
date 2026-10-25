package demoday.backend.payment.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Getter
@Entity
@Table(
        name = "payment",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_order_number",
                        columnNames = "order_number"
                ),
                @UniqueConstraint(
                        name = "uk_payment_provider_transaction_id",
                        columnNames = "provider_transaction_id"
                )
        }
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Payment {

    private static final Pattern ORDER_NUMBER_PATTERN =
            Pattern.compile("^[A-Za-z0-9]{1,40}$");

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

    @Column(name = "ordered_product_code", nullable = false, length = 50)
    private String orderedProductCode;

    @Column(name = "ordered_product_name", nullable = false, length = 50)
    private String orderedProductName;

    @Enumerated(EnumType.STRING)
    @Column(name = "ordered_product_type", nullable = false, length = 20)
    private ProductType orderedProductType;

    @Column(name = "ordered_pass_duration_hours")
    private Integer orderedPassDurationHours;

    @Column(
            name = "order_number",
            nullable = false,
            length = 40
    )
    private String orderNumber;

    @Column(
            name = "provider_transaction_id",
            length = 100
    )
    private String providerTransactionId;

    @Column(
            nullable = false,
            check = @CheckConstraint(
                    name = "ck_payment_amount_minimum",
                    constraint = "amount >= 100"
            )
    )
    private Integer amount;

    @Column(
            name = "pay_method",
            length = 20
    )
    private String payMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    @Column(name = "result_code", length = 20)
    private String resultCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    public static Payment create(
            Member member,
            Product product,
            String orderNumber,
            Integer amount,
            LocalDateTime requestedAt
    ) {
        validateRequiredValues(
                member,
                product,
                orderNumber,
                amount,
                requestedAt
        );

        return Payment.builder()
                .member(member)
                .product(product)
                .orderedProductCode(product.getProductCode())
                .orderedProductName(product.getName())
                .orderedProductType(product.getProductType())
                .orderedPassDurationHours(product.getPassDurationHours())
                .orderNumber(orderNumber)
                .amount(amount)
                .status(PaymentStatus.READY)
                .requestedAt(requestedAt)
                .build();
    }

    public void markPaymentRequested(String providerTransactionId) {
        if (status != PaymentStatus.READY) {
            throw new IllegalStateException(
                    "결제 요청 상태로 변경할 수 없습니다."
            );
        }

        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new IllegalArgumentException(
                    "PG 거래번호는 필수입니다."
            );
        }

        this.providerTransactionId = providerTransactionId;
        this.status = PaymentStatus.PAYMENT_REQUESTED;
    }

    public void approve(
            String providerTransactionId,
            String resultCode,
            String payMethod,
            LocalDateTime approvedAt
    ) {
        if (status != PaymentStatus.READY
                && status != PaymentStatus.PAYMENT_REQUESTED) {
            throw new IllegalStateException(
                    "결제를 승인할 수 없는 상태입니다."
            );
        }

        if (providerTransactionId == null || providerTransactionId.isBlank()) {
            throw new IllegalArgumentException(
                    "PG 거래번호는 필수입니다."
            );
        }

        if (approvedAt == null) {
            throw new IllegalArgumentException(
                    "승인 시각은 필수입니다."
            );
        }

        this.providerTransactionId = providerTransactionId;
        this.resultCode = resultCode;
        this.payMethod = payMethod;
        this.approvedAt = approvedAt;
        this.failureMessage = null;
        this.status = PaymentStatus.APPROVED;
    }

    public void complete(LocalDateTime completedAt) {
        if (status == PaymentStatus.COMPLETED) {
            return;
        }

        if (status != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                    "승인된 결제만 상품 지급을 완료할 수 있습니다."
            );
        }

        if (completedAt == null) {
            throw new IllegalArgumentException(
                    "상품 지급 완료 시각은 필수입니다."
            );
        }

        this.completedAt = completedAt;
        this.failureMessage = null;
        this.status = PaymentStatus.COMPLETED;
    }

    public void fail(
            String resultCode,
            String failureMessage
    ) {
        if (status == PaymentStatus.APPROVED
                || status == PaymentStatus.COMPLETED
                || status == PaymentStatus.CANCEL_REQUESTED
                || status == PaymentStatus.REFUNDED
                || status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "실패 상태로 변경할 수 없는 결제입니다."
            );
        }

        this.resultCode = resultCode;
        this.failureMessage = normalizeMessage(failureMessage);
        this.status = PaymentStatus.FAILED;
    }

    public void cancel(LocalDateTime cancelledAt) {
        if (status == PaymentStatus.CANCELLED) {
            return;
        }

        if (status != PaymentStatus.APPROVED
                && status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "승인된 결제만 취소할 수 있습니다."
            );
        }

        if (cancelledAt == null) {
            throw new IllegalArgumentException(
                    "결제 취소 시각은 필수입니다."
            );
        }

        this.cancelledAt = cancelledAt;
        this.status = PaymentStatus.CANCELLED;
    }

    public PaymentStatus requestCancellation() {
        if (status != PaymentStatus.APPROVED
                && status != PaymentStatus.COMPLETED) {
            throw new IllegalStateException(
                    "승인 또는 지급 완료 결제만 환불을 요청할 수 있습니다."
            );
        }

        PaymentStatus previousStatus = status;
        this.status = PaymentStatus.CANCEL_REQUESTED;
        return previousStatus;
    }

    public void restoreAfterCancellationFailure(
            PaymentStatus previousStatus
    ) {
        if (status != PaymentStatus.CANCEL_REQUESTED
                || (previousStatus != PaymentStatus.APPROVED
                && previousStatus != PaymentStatus.COMPLETED)) {
            throw new IllegalStateException(
                    "결제 취소 요청 상태를 복구할 수 없습니다."
            );
        }

        this.status = previousStatus;
    }

    public void refund(LocalDateTime refundedAt) {
        if (status == PaymentStatus.REFUNDED) {
            return;
        }

        if (status != PaymentStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException(
                    "취소 요청 중인 결제만 환불 완료할 수 있습니다."
            );
        }

        if (refundedAt == null) {
            throw new IllegalArgumentException(
                    "환불 완료 시각은 필수입니다."
            );
        }

        this.cancelledAt = refundedAt;
        this.failureMessage = null;
        this.status = PaymentStatus.REFUNDED;
    }

    private static void validateRequiredValues(
            Member member,
            Product product,
            String orderNumber,
            Integer amount,
            LocalDateTime requestedAt
    ) {
        if (member == null) {
            throw new IllegalArgumentException(
                    "결제 회원은 필수입니다."
            );
        }

        if (product == null) {
            throw new IllegalArgumentException(
                    "결제 상품은 필수입니다."
            );
        }

        if (orderNumber == null
                || !ORDER_NUMBER_PATTERN.matcher(orderNumber).matches()) {
            throw new IllegalArgumentException(
                    "주문번호는 40자 이하의 영문과 숫자만 사용할 수 있습니다."
            );
        }

        if (amount == null || amount < 100) {
            throw new IllegalArgumentException(
                    "결제 금액은 100원 이상이어야 합니다."
            );
        }

        if (requestedAt == null) {
            throw new IllegalArgumentException(
                    "주문 요청 시각은 필수입니다."
            );
        }

        if (!amount.equals(product.getPrice())) {
            throw new IllegalArgumentException(
                    "주문 금액은 상품 가격과 일치해야 합니다."
            );
        }
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }

        String normalized = message.trim();

        if (normalized.length() <= 500) {
            return normalized;
        }

        return normalized.substring(0, 500);
    }

    public void recordFulfillmentFailure(String message) {
        if (status != PaymentStatus.APPROVED) {
            throw new IllegalStateException(
                    "승인된 결제에만 지급 실패를 기록할 수 있습니다."
            );
        }

        this.failureMessage = normalizeMessage(message);
    }

    public void clearFailureMessage() {
        this.failureMessage = null;
    }

    public void recordCancellationFailure(String message) {
        this.failureMessage = normalizeMessage(message);
    }
}
