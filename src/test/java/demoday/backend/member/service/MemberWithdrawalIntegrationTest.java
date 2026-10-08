package demoday.backend.member.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.domain.Product;
import demoday.backend.payment.repository.PaymentRepository;
import demoday.backend.payment.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-withdrawal;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@Import(MemberWithdrawalIntegrationTest.FixedClockConfig.class)
class MemberWithdrawalIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW = Instant.parse("2026-10-07T06:30:00Z");
    private static final LocalDateTime NOW_LOCAL = LocalDateTime.ofInstant(NOW, KST);

    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PaymentRepository paymentRepository;

    private Member member;
    private Product product;

    @BeforeEach
    void setUp() {
        long sequence = SEQUENCE.incrementAndGet();
        member = memberRepository.saveAndFlush(
                Member.create(970000L + sequence, "w" + sequence)
        );
        product = productRepository.saveAndFlush(
                Product.create(
                        "WF" + sequence,
                        ProductType.FISH,
                        "생선 100개",
                        1000,
                        100,
                        null,
                        null,
                        null,
                        true
                )
        );
    }

    @Test
    @DisplayName("유효 시간이 남은 READY 결제가 있으면 탈퇴할 수 없다")
    void activeReadyPaymentBlocksWithdrawal() {
        savePayment(PaymentStatus.READY, NOW_LOCAL.minusMinutes(29));

        assertWithdrawalBlocked();
    }

    @Test
    @DisplayName("정확히 30분이 지난 READY 결제는 탈퇴를 막지 않는다")
    void readyPaymentAtExpirationBoundaryAllowsWithdrawal() {
        savePayment(PaymentStatus.READY, NOW_LOCAL.minusMinutes(30));

        memberService.withdraw(member.getMemberId());

        assertWithdrawn();
    }

    @Test
    @DisplayName("유효 시간이 지난 READY 결제는 탈퇴를 막지 않는다")
    void expiredReadyPaymentAllowsWithdrawal() {
        savePayment(PaymentStatus.READY, NOW_LOCAL.minusMinutes(31));

        memberService.withdraw(member.getMemberId());

        assertWithdrawn();
    }

    @ParameterizedTest(name = "{0} 결제가 있으면 탈퇴할 수 없다")
    @EnumSource(
            value = PaymentStatus.class,
            names = {"CONFIRMING", "UNKNOWN", "APPROVED"}
    )
    void unresolvedPaymentBlocksWithdrawal(PaymentStatus paymentStatus) {
        savePayment(paymentStatus, NOW_LOCAL.minusMinutes(1));

        assertWithdrawalBlocked();
    }

    @ParameterizedTest(name = "{0} 결제만 있으면 탈퇴할 수 있다")
    @EnumSource(
            value = PaymentStatus.class,
            names = {"COMPLETED", "FAILED", "CANCELLED"}
    )
    void terminalPaymentAllowsWithdrawal(PaymentStatus paymentStatus) {
        savePayment(paymentStatus, NOW_LOCAL.minusMinutes(1));

        memberService.withdraw(member.getMemberId());

        assertWithdrawn();
    }

    private void assertWithdrawalBlocked() {
        assertThatThrownBy(() -> memberService.withdraw(member.getMemberId()))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        MemberErrorCode
                                                .WITHDRAWAL_BLOCKED_BY_PENDING_PAYMENT
                                )
                );

        assertThat(memberRepository.findById(member.getMemberId())
                .orElseThrow()
                .getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }

    private void assertWithdrawn() {
        Member withdrawn = memberRepository.findById(member.getMemberId())
                .orElseThrow();

        assertThat(withdrawn.getStatus()).isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(withdrawn.getDeletedAt()).isEqualTo(NOW_LOCAL);
    }

    private Payment savePayment(
            PaymentStatus targetStatus,
            LocalDateTime requestedAt
    ) {
        long sequence = SEQUENCE.incrementAndGet();
        Payment payment = Payment.create(
                member,
                product,
                "W" + sequence,
                product.getPrice(),
                "20261007153000",
                requestedAt
        );
        String paymentKey = "withdrawal-key-" + sequence;

        switch (targetStatus) {
            case READY -> {
            }
            case CONFIRMING -> payment.startConfirmation(
                    paymentKey,
                    "testmid",
                    NOW_LOCAL.minusSeconds(30)
            );
            case UNKNOWN -> {
                payment.startConfirmation(
                        paymentKey,
                        "testmid",
                        NOW_LOCAL.minusMinutes(1)
                );
                payment.markUnknown("3004", "승인 결과 확인 필요");
            }
            case APPROVED -> approve(payment, paymentKey, sequence);
            case COMPLETED -> {
                approve(payment, paymentKey, sequence);
                payment.complete(NOW_LOCAL);
            }
            case FAILED -> payment.fail("A001", "결제 인증 실패");
            case CANCELLED -> {
                approve(payment, paymentKey, sequence);
                payment.cancel(NOW_LOCAL);
            }
        }

        return paymentRepository.saveAndFlush(payment);
    }

    private void approve(
            Payment payment,
            String paymentKey,
            long sequence
    ) {
        payment.startConfirmation(
                paymentKey,
                "testmid",
                NOW_LOCAL.minusMinutes(1)
        );
        payment.approve(
                "withdrawal-tid-" + sequence,
                "3001",
                "card",
                NOW_LOCAL.minusSeconds(30)
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, KST);
        }
    }
}
