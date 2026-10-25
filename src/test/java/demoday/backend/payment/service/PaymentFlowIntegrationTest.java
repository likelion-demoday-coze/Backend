package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.client.PayAppClient;
import demoday.backend.payment.client.dto.PayAppCancelResponse;
import demoday.backend.payment.client.dto.PayAppPaymentResponse;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.domain.Product;
import demoday.backend.payment.dto.PayAppFeedbackRequest;
import demoday.backend.payment.dto.PaymentOrderRequest;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.repository.PaymentRepository;
import demoday.backend.payment.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment-flow;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false",
        "app.payment.fulfillment-retry-delay-ms=3600000"
})
@AutoConfigureMockMvc
@Import(PaymentFlowIntegrationTest.FixedClockConfig.class)
class PaymentFlowIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW =
            Instant.parse("2026-10-25T03:00:00Z");
    private static final LocalDateTime NOW_KST =
            LocalDateTime.ofInstant(NOW, KST);

    @Autowired private PaymentOrderService orderService;
    @Autowired private PayAppFeedbackService feedbackService;
    @Autowired private PaymentFulfillmentRetryService retryService;
    @Autowired private PaymentCancellationService cancellationService;
    @Autowired private MemberPassQueryService passQueryService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MemberPassRepository memberPassRepository;
    @Autowired private MockMvc mockMvc;

    @MockitoBean private PayAppClient payAppClient;
    @MockitoSpyBean private PaymentFulfillmentService fulfillmentService;

    private Member member;
    private Product product;

    @BeforeEach
    void setUp() {
        reset(payAppClient, fulfillmentService);

        memberPassRepository.deleteAll();
        paymentRepository.deleteAll();
        productRepository.deleteAll();
        memberRepository.deleteAll();

        long sequence = SEQUENCE.incrementAndGet();

        member = memberRepository.saveAndFlush(
                Member.create(
                        8_000_000L + sequence,
                        "p" + sequence
                )
        );

        product = productRepository.saveAndFlush(
                Product.create(
                        "WEEK_PASS_" + sequence,
                        ProductType.PASS,
                        "일주일 패스",
                        1900,
                        168,
                        NOW_KST.minusDays(1),
                        NOW_KST.plusDays(10),
                        true
                )
        );

        when(payAppClient.requestPayment(any()))
                .thenAnswer(invocation -> {
                    var request = invocation.getArgument(0,
                            demoday.backend.payment.client.dto.PayAppPaymentRequest.class);

                    return new PayAppPaymentResponse(
                            true,
                            null,
                            null,
                            transactionId(request.orderNumber()),
                            "https://payapp.test/pay/" + request.orderNumber()
                    );
                });

        when(payAppClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PayAppCancelResponse(
                        true,
                        null,
                        null
                ));
    }

    @Test
    @DisplayName("주문 생성 시 서버 상품 정보로 PAYMENT_REQUESTED 주문을 저장한다")
    void createsPaymentOrderFromServerProduct() {
        var response = createOrder();

        Payment payment = paymentRepository
                .findByOrderNumber(response.orderNumber())
                .orElseThrow();

        assertThat(response.status())
                .isEqualTo(PaymentStatus.PAYMENT_REQUESTED);
        assertThat(response.amount()).isEqualTo(1900);
        assertThat(response.paymentUrl())
                .contains(response.orderNumber());
        assertThat(payment.getOrderedProductName())
                .isEqualTo("일주일 패스");
        assertThat(payment.getOrderedPassDurationHours())
                .isEqualTo(168);
        assertThat(payment.getProviderTransactionId())
                .isEqualTo(transactionId(response.orderNumber()));
    }

    @Test
    @DisplayName("결제 완료 Feedback은 패스를 한 번 지급하고 COMPLETED로 변경한다")
    void feedbackCompletesPaymentAndIssuesPassOnce() {
        var order = createOrder();

        feedbackService.process(feedback(order.orderNumber()));
        feedbackService.process(feedback(order.orderNumber()));

        Payment payment = paymentRepository
                .findByOrderNumber(order.orderNumber())
                .orElseThrow();
        MemberPass memberPass = memberPassRepository
                .findByPaymentPaymentId(payment.getPaymentId())
                .orElseThrow();

        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(memberPassRepository.count()).isEqualTo(1);
        assertThat(memberPass.getStartedAt()).isEqualTo(NOW_KST);
        assertThat(memberPass.getExpiresAt())
                .isEqualTo(NOW_KST.plusHours(168));
    }

    @Test
    @DisplayName("Feedback 인증값과 주문 정보 위변조를 거절한다")
    void rejectsForgedFeedback() {
        var order = createOrder();

        PayAppFeedbackRequest invalidAuthentication = copyFeedback(
                feedback(order.orderNumber()),
                "wrong-link-value",
                "1900"
        );

        assertThatThrownBy(() ->
                feedbackService.process(invalidAuthentication)
        ).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                PaymentErrorCode
                                        .INVALID_FEEDBACK_AUTHENTICATION
                        )
        );

        PayAppFeedbackRequest invalidAmount = copyFeedback(
                feedback(order.orderNumber()),
                "test-link-value",
                "2000"
        );

        assertThatThrownBy(() ->
                feedbackService.process(invalidAmount)
        ).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                PaymentErrorCode
                                        .PAYMENT_INFORMATION_MISMATCH
                        )
        );

        assertThat(memberPassRepository.count()).isZero();
    }

    @Test
    @DisplayName("패스 지급 실패 시 APPROVED를 보존하고 재처리로 지급한다")
    void retriesFailedFulfillment() {
        var order = createOrder();

        doThrow(new IllegalStateException("temporary failure"))
                .when(fulfillmentService)
                .fulfill(any(), any());

        assertThatThrownBy(() ->
                feedbackService.process(feedback(order.orderNumber()))
        ).isInstanceOf(IllegalStateException.class);

        Payment approved = paymentRepository
                .findByOrderNumber(order.orderNumber())
                .orElseThrow();

        assertThat(approved.getStatus())
                .isEqualTo(PaymentStatus.APPROVED);
        assertThat(approved.getFailureMessage()).isNotBlank();
        assertThat(memberPassRepository.count()).isZero();

        reset(fulfillmentService);
        retryService.retryPendingFulfillments();

        Payment completed = paymentRepository
                .findById(approved.getPaymentId())
                .orElseThrow();

        assertThat(completed.getStatus())
                .isEqualTo(PaymentStatus.COMPLETED);
        assertThat(completed.getFailureMessage()).isNull();
        assertThat(memberPassRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 패스와 이용 내역 API를 인증 회원 기준으로 조회한다")
    void exposesCurrentPassAndHistoryApis() throws Exception {
        var order = createOrder();
        feedbackService.process(feedback(order.orderNumber()));

        mockMvc.perform(
                        get("/api/v1/passes/me/current")
                                .with(memberAuthentication())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.active").value(true))
                .andExpect(jsonPath("$.result.pass.status")
                        .value("ACTIVE"))
                .andExpect(jsonPath("$.result.pass.passType")
                        .value("SEVEN_DAY"));

        mockMvc.perform(
                        get("/api/v1/passes/me")
                                .with(memberAuthentication())
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements")
                        .value(1))
                .andExpect(jsonPath("$.result.passes[0].orderNumber")
                        .value(order.orderNumber()));

        mockMvc.perform(get("/api/v1/passes/me/current"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("결제와 패스 내역 API는 잘못된 페이지 요청을 400으로 거절한다")
    void rejectsInvalidHistoryPageRequests() throws Exception {
        mockMvc.perform(
                        get("/api/v1/payments")
                                .param("page", "-1")
                                .param("size", "20")
                                .with(memberAuthentication())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_400_2"));

        mockMvc.perform(
                        get("/api/v1/passes/me")
                                .param("page", "0")
                                .param("size", "0")
                                .with(memberAuthentication())
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("PAYMENT_400_2"));
    }

    @Test
    @DisplayName("Feedback 엔드포인트는 CSRF 없이 평문 SUCCESS를 반환한다")
    void feedbackEndpointReturnsPlainSuccess() throws Exception {
        var order = createOrder();
        var feedback = feedback(order.orderNumber());

        mockMvc.perform(
                        post("/api/v1/payments/feedback")
                                .contentType(
                                        MediaType.APPLICATION_FORM_URLENCODED
                                )
                                .param("userid", feedback.userId())
                                .param("linkkey", feedback.linkKey())
                                .param("linkval", feedback.linkValue())
                                .param("goodname", feedback.productName())
                                .param("price", feedback.price())
                                .param("pay_date", feedback.approvedAt())
                                .param("pay_type", feedback.payType())
                                .param("pay_state", feedback.payState())
                                .param("var1", feedback.orderNumber())
                                .param("mul_no", feedback.transactionId())
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.TEXT_PLAIN
                ))
                .andExpect(content().string("SUCCESS"));
    }

    @Test
    @Timeout(30)
    @DisplayName("동일 Feedback을 동시에 처리해도 패스는 한 번만 지급한다")
    void concurrentFeedbackIssuesPassOnce() throws Exception {
        var order = createOrder();
        PayAppFeedbackRequest feedback =
                feedback(order.orderNumber());

        runConcurrently(() -> {
            feedbackService.process(feedback);
            return true;
        });

        assertThat(memberPassRepository.count()).isEqualTo(1);
        assertThat(paymentRepository
                .findByOrderNumber(order.orderNumber())
                .orElseThrow()
                .getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @Timeout(30)
    @DisplayName("동시에 주문을 생성해도 진행 중 주문은 하나만 생성된다")
    void concurrentOrderCreationAllowsOneOrder() throws Exception {
        List<Boolean> results = runConcurrently(() -> {
            try {
                createOrder();
                return true;
            } catch (ProjectException exception) {
                assertThat(exception.getErrorCode())
                        .isEqualTo(PaymentErrorCode.PAYMENT_IN_PROGRESS);
                return false;
            }
        });

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("활성 패스가 있으면 새 결제 주문을 생성할 수 없다")
    void activePassBlocksNewOrder() {
        var order = createOrder();
        feedbackService.process(feedback(order.orderNumber()));

        assertThatThrownBy(this::createOrder)
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(
                                        PaymentErrorCode.ACTIVE_PASS_EXISTS
                                )
                );
    }

    @Test
    @DisplayName("기존 패스가 만료되면 다시 구매할 수 있다")
    void expiredPassAllowsNewOrder() {
        long sequence = SEQUENCE.incrementAndGet();
        Payment previousPayment = Payment.create(
                member,
                product,
                "PREVIOUS" + sequence,
                product.getPrice(),
                NOW_KST.minusDays(8)
        );
        previousPayment.approve(
                "previous-tx-" + sequence,
                "4",
                "card",
                NOW_KST.minusDays(8)
        );
        previousPayment.complete(NOW_KST.minusDays(8));
        paymentRepository.saveAndFlush(previousPayment);
        memberPassRepository.saveAndFlush(MemberPass.create(
                member,
                previousPayment,
                demoday.backend.payment.code.PassType.SEVEN_DAY,
                NOW_KST.minusDays(8),
                NOW_KST.minusDays(1)
        ));

        var newOrder = createOrder();

        assertThat(newOrder.status())
                .isEqualTo(PaymentStatus.PAYMENT_REQUESTED);
    }

    @Test
    @DisplayName("지급 완료된 패스를 전액 환불하면 결제와 패스를 함께 종료한다")
    void refundsCompletedPaymentAndCancelsPass() {
        var order = createOrder();
        feedbackService.process(feedback(order.orderNumber()));

        Payment payment = paymentRepository
                .findByOrderNumber(order.orderNumber())
                .orElseThrow();

        var response = cancellationService.cancel(
                member.getMemberId(),
                payment.getPaymentId()
        );

        Payment refunded = paymentRepository
                .findById(payment.getPaymentId())
                .orElseThrow();
        MemberPass cancelledPass = memberPassRepository
                .findByPaymentPaymentId(payment.getPaymentId())
                .orElseThrow();

        assertThat(response.status())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);
        assertThat(refunded.getCancelledAt()).isEqualTo(NOW_KST);
        assertThat(cancelledPass.getStatus())
                .isEqualTo(PassStatus.CANCELLED);
        assertThat(cancelledPass.getCancelledAt())
                .isEqualTo(NOW_KST);
    }

    @Test
    @DisplayName("동일 결제를 다시 취소해도 페이앱 취소 API는 한 번만 호출한다")
    void duplicateCancellationIsIdempotent() {
        var order = createOrder();
        feedbackService.process(feedback(order.orderNumber()));
        Payment payment = paymentRepository
                .findByOrderNumber(order.orderNumber())
                .orElseThrow();

        cancellationService.cancel(
                member.getMemberId(),
                payment.getPaymentId()
        );
        var duplicate = cancellationService.cancel(
                member.getMemberId(),
                payment.getPaymentId()
        );

        assertThat(duplicate.status())
                .isEqualTo(PaymentStatus.REFUNDED);
        verify(payAppClient, times(1))
                .cancelPayment(anyString(), anyString());
    }

    @Test
    @DisplayName("페이앱이 취소를 거절하면 결제와 패스 상태를 유지한다")
    void restoresPaymentWhenProviderRejectsCancellation() {
        var order = createOrder();
        feedbackService.process(feedback(order.orderNumber()));
        Payment payment = paymentRepository
                .findByOrderNumber(order.orderNumber())
                .orElseThrow();

        when(payAppClient.cancelPayment(anyString(), anyString()))
                .thenReturn(new PayAppCancelResponse(
                        false,
                        "CANCEL_REJECTED",
                        "취소 거절"
                ));

        assertThatThrownBy(() -> cancellationService.cancel(
                member.getMemberId(),
                payment.getPaymentId()
        )).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                PaymentErrorCode.PAYAPP_CANCEL_FAILED
                        )
        );

        assertThat(paymentRepository.findById(payment.getPaymentId())
                .orElseThrow()
                .getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(memberPassRepository
                .findByPaymentPaymentId(payment.getPaymentId())
                .orElseThrow()
                .getStatus()).isEqualTo(PassStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 만료된 패스 결제는 환불할 수 없다")
    void rejectsRefundForExpiredPass() {
        long sequence = SEQUENCE.incrementAndGet();
        Payment payment = Payment.create(
                member,
                product,
                "EXPIRED" + sequence,
                product.getPrice(),
                NOW_KST.minusDays(9)
        );
        payment.approve(
                "expired-tx-" + sequence,
                "4",
                "card",
                NOW_KST.minusDays(8)
        );
        payment.complete(NOW_KST.minusDays(8));
        paymentRepository.saveAndFlush(payment);
        memberPassRepository.saveAndFlush(MemberPass.create(
                member,
                payment,
                demoday.backend.payment.code.PassType.SEVEN_DAY,
                NOW_KST.minusDays(8),
                NOW_KST.minusDays(1)
        ));

        assertThatThrownBy(() -> cancellationService.cancel(
                member.getMemberId(),
                payment.getPaymentId()
        )).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(
                                PaymentErrorCode.PASS_ALREADY_EXPIRED
                        )
        );

        verify(payAppClient, times(0))
                .cancelPayment(anyString(), anyString());
    }

    @Test
    @Timeout(30)
    @DisplayName("동시에 취소를 요청해도 페이앱 취소 API는 한 번만 호출한다")
    void concurrentCancellationCallsProviderOnce() throws Exception {
        var order = createOrder();
        feedbackService.process(feedback(order.orderNumber()));
        Payment payment = paymentRepository
                .findByOrderNumber(order.orderNumber())
                .orElseThrow();

        List<Boolean> results = runConcurrently(() -> {
            try {
                cancellationService.cancel(
                        member.getMemberId(),
                        payment.getPaymentId()
                );
                return true;
            } catch (ProjectException exception) {
                assertThat(exception.getErrorCode())
                        .isEqualTo(
                                PaymentErrorCode
                                        .CANCELLATION_IN_PROGRESS
                        );
                return false;
            }
        });

        assertThat(results.stream().filter(Boolean::booleanValue).count())
                .isGreaterThanOrEqualTo(1);
        verify(payAppClient, times(1))
                .cancelPayment(anyString(), anyString());
    }

    private demoday.backend.payment.dto.PaymentOrderResponse createOrder() {
        return orderService.createOrder(
                member.getMemberId(),
                new PaymentOrderRequest(
                        product.getProductCode(),
                        "01012345678"
                )
        );
    }

    private PayAppFeedbackRequest feedback(String orderNumber) {
        return new PayAppFeedbackRequest(
                "test-user",
                "test-link-key",
                "test-link-value",
                "일주일 패스",
                "1900",
                "2026-10-25 12:00:00",
                "1",
                "4",
                orderNumber,
                transactionId(orderNumber),
                "0"
        );
    }

    private PayAppFeedbackRequest copyFeedback(
            PayAppFeedbackRequest source,
            String linkValue,
            String price
    ) {
        return new PayAppFeedbackRequest(
                source.userId(),
                source.linkKey(),
                linkValue,
                source.productName(),
                price,
                source.approvedAt(),
                source.payType(),
                source.payState(),
                source.orderNumber(),
                source.transactionId(),
                source.feedbackType()
        );
    }

    private String transactionId(String orderNumber) {
        return "TX" + orderNumber;
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor
    memberAuthentication() {
        return authentication(
                new UsernamePasswordAuthenticationToken(
                        member.getMemberId(),
                        null,
                        List.of(new SimpleGrantedAuthority(
                                "ROLE_MEMBER"
                        ))
                )
        );
    }

    private <T> List<T> runConcurrently(
            Callable<T> operation
    ) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Callable<T> synchronizedOperation = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return operation.call();
            };

            var first = executor.submit(synchronizedOperation);
            var second = executor.submit(synchronizedOperation);

            return List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
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
