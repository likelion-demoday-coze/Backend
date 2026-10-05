package demoday.backend.payment.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.payment.code.PaymentStatus;
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
                        name = "uk_payment_payment_key",
                        columnNames = "payment_key"
                ),
                @UniqueConstraint(
                        name = "uk_payment_tid",
                        columnNames = "tid"
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

    @Column(
            name = "order_number",
            nullable = false,
            length = 40
    )
    private String orderNumber;

    @Column(
            name = "payment_key",
            length = 255
    )
    private String paymentKey;

    @Column(
            name = "tid",
            length = 100
    )
    private String tid;

    @Column(
            name = "merchant_id",
            length = 10
    )
    private String merchantId;

    @Column(
            nullable = false,
            check = @CheckConstraint(
                    name = "ck_payment_amount_minimum",
                    constraint = "amount >= 100"
            )
    )
    private Integer amount;

    @Column(
            name = "edi_date",
            nullable = false,
            length = 14
    )
    private String ediDate;

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
            String ediDate,
            LocalDateTime requestedAt
    ) {
        validateRequiredValues(
                member,
                product,
                orderNumber,
                amount,
                ediDate,
                requestedAt
        );

        return Payment.builder()
                .member(member)
                .product(product)
                .orderNumber(orderNumber)
                .amount(amount)
                .ediDate(ediDate)
                .status(PaymentStatus.READY)
                .requestedAt(requestedAt)
                .build();
    }

    public void recordAuthentication(
            String paymentKey,
            String merchantId
    ) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalArgumentException(
                    "결제 키는 필수입니다."
            );
        }

        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalArgumentException(
                    "가맹점 ID는 필수입니다."
            );
        }

        if (status != PaymentStatus.READY
                && status != PaymentStatus.UNKNOWN) {
            throw new IllegalStateException(
                    "결제 인증 정보를 기록할 수 없는 상태입니다."
            );
        }

        if (this.paymentKey != null
                && !this.paymentKey.equals(paymentKey)) {
            throw new IllegalStateException(
                    "이미 다른 결제 키가 연결된 주문입니다."
            );
        }

        this.paymentKey = paymentKey;
        this.merchantId = merchantId;
    }

    public void approve(
            String tid,
            String resultCode,
            String payMethod,
            LocalDateTime approvedAt
    ) {
        if (status != PaymentStatus.READY
                && status != PaymentStatus.UNKNOWN) {
            throw new IllegalStateException(
                    "결제를 승인할 수 없는 상태입니다."
            );
        }

        if (paymentKey == null || paymentKey.isBlank()) {
            throw new IllegalStateException(
                    "결제 키가 등록되지 않았습니다."
            );
        }

        if (tid == null || tid.isBlank()) {
            throw new IllegalArgumentException(
                    "PG 거래번호는 필수입니다."
            );
        }

        if (!"3001".equals(resultCode)) {
            throw new IllegalArgumentException(
                    "승인 성공 코드가 아닙니다."
            );
        }

        if (approvedAt == null) {
            throw new IllegalArgumentException(
                    "승인 시각은 필수입니다."
            );
        }

        this.tid = tid;
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
        this.status = PaymentStatus.COMPLETED;
    }

    public void fail(
            String resultCode,
            String failureMessage
    ) {
        if (status == PaymentStatus.APPROVED
                || status == PaymentStatus.COMPLETED
                || status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "실패 상태로 변경할 수 없는 결제입니다."
            );
        }

        this.resultCode = resultCode;
        this.failureMessage = normalizeMessage(failureMessage);
        this.status = PaymentStatus.FAILED;
    }

    public void markUnknown(
            String resultCode,
            String message
    ) {
        if (status == PaymentStatus.APPROVED
                || status == PaymentStatus.COMPLETED
                || status == PaymentStatus.CANCELLED) {
            throw new IllegalStateException(
                    "승인 결과 확인 필요 상태로 변경할 수 없습니다."
            );
        }

        this.resultCode = resultCode;
        this.failureMessage = normalizeMessage(message);
        this.status = PaymentStatus.UNKNOWN;
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

    private static void validateRequiredValues(
            Member member,
            Product product,
            String orderNumber,
            Integer amount,
            String ediDate,
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

        if (ediDate == null
                || !ediDate.matches("^\\d{14}$")) {
            throw new IllegalArgumentException(
                    "전문 생성 일시는 yyyyMMddHHmmss 형식이어야 합니다."
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
}
