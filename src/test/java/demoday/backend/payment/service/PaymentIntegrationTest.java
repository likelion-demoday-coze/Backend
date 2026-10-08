package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.client.KorpayClient;
import demoday.backend.payment.client.dto.KorpayConfirmResponse;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.domain.Product;
import demoday.backend.payment.dto.PaymentOrderResponse;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.repository.PaymentRepository;
import demoday.backend.payment.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:payment;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(PaymentIntegrationTest.FixedClockConfig.class)
class PaymentIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Instant NOW = Instant.parse("2026-10-05T06:30:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private PaymentOrderService orderService;
    @Autowired private PaymentProcessingService processingService;
    @Autowired private MemberRepository members;
    @Autowired private ProductRepository products;
    @Autowired private PaymentRepository payments;
    @Autowired private MemberPassRepository memberPasses;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private KorpayClient korpayClient;
    @MockitoSpyBean private PaymentFulfillmentService fulfillmentService;

    private Member member;
    private Product fishProduct;

    @BeforeEach
    void setUp() {
        reset(korpayClient, fulfillmentService);

        long sequence = SEQUENCE.incrementAndGet();
        member = members.saveAndFlush(
                Member.create(800000L + sequence, "pay" + sequence)
        );
        fishProduct = products.saveAndFlush(
                Product.create(
                        "FISH" + sequence,
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
    void orderCreationRequiresAuthenticationAndCsrf() throws Exception {
        String body = "{\"productCode\":\"" + fishProduct.getProductCode() + "\"}";

        mvc.perform(post("/api/v1/payments/orders")
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/payments/orders")
                        .with(authentication(authToken(member.getMemberId())))
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/payments/orders")
                        .with(authentication(authToken(member.getMemberId())))
                        .with(csrf())
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.result.merchantId").value("testmid"))
                .andExpect(jsonPath("$.result.amount").value(1000))
                .andExpect(jsonPath("$.result.payMethod").value("card"))
                .andExpect(jsonPath("$.result.reserved")
                        .value("http://localhost:3000"))
                .andExpect(jsonPath("$.result.hashKey").isString());
    }

    @Test
    void orderUsesServerPriceAndOfficialHashFormula() throws Exception {
        PaymentOrderResponse response = createOrder();

        String source = "testmid"
                + response.ediDate()
                + fishProduct.getPrice()
                + "test-mkey";
        String expectedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(source.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(response.amount()).isEqualTo(1000);
        assertThat(response.hashKey()).isEqualTo(expectedHash);
        assertThat(response.orderNumber()).matches("[A-Za-z0-9]{1,40}");
        assertThat(payments.findByOrderNumber(response.orderNumber()))
                .get()
                .extracting(Payment::getStatus)
                .isEqualTo(PaymentStatus.READY);
    }

    @Test
    void withdrawalCompletedBeforeMemberLockPreventsNewOrder() throws Exception {
        long paymentCountBefore = payments.count();
        CountDownLatch withdrawalLocked = new CountDownLatch(1);
        CountDownLatch allowWithdrawalCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            var withdrawalFuture = executor.submit(() -> {
                transactionTemplate.executeWithoutResult(status -> {
                    Member lockedMember = members
                            .findByIdForUpdate(member.getMemberId())
                            .orElseThrow();
                    lockedMember.withdraw(LocalDateTime.now());
                    members.saveAndFlush(lockedMember);
                    withdrawalLocked.countDown();

                    try {
                        if (!allowWithdrawalCommit.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "탈퇴 트랜잭션 커밋 신호를 기다리지 못했습니다."
                            );
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });
            });

            assertThat(withdrawalLocked.await(5, TimeUnit.SECONDS)).isTrue();

            var orderFuture = executor.submit(() ->
                    orderService.createOrder(
                            member.getMemberId(),
                            fishProduct.getProductCode()
                    )
            );

            allowWithdrawalCommit.countDown();
            withdrawalFuture.get(5, TimeUnit.SECONDS);

            assertThatThrownBy(() -> orderFuture.get(5, TimeUnit.SECONDS))
                    .isInstanceOfSatisfying(
                            ExecutionException.class,
                            exception -> assertThat(exception.getCause())
                                    .isInstanceOfSatisfying(
                                            ProjectException.class,
                                            projectException -> assertThat(
                                                    projectException.getErrorCode()
                                            ).isEqualTo(
                                                    MemberErrorCode.INACTIVE_MEMBER
                                            )
                                    )
                    );

            assertThat(payments.count()).isEqualTo(paymentCountBefore);
        } finally {
            allowWithdrawalCommit.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void approvalAndFulfillmentUseProductTermsCapturedAtOrderTime() throws Exception {
        PaymentOrderResponse order = createOrder();
        String paymentKey = "snapshot-key-" + SEQUENCE.incrementAndGet();

        jdbcTemplate.update(
                "UPDATE product SET name = ?, fish_amount = ? WHERE product_id = ?",
                "변경된 생선 상품",
                999,
                fishProduct.getProductId()
        );
        when(korpayClient.confirm(paymentKey)).thenReturn(
                approvedResponse(order, paymentKey)
        );

        performCallback(order, paymentKey)
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:3000/payment/success?orderNumber="
                                + order.orderNumber()
                ));

        Payment saved = payments.findByOrderNumber(order.orderNumber())
                .orElseThrow();
        assertThat(saved.getOrderedProductName()).isEqualTo("생선 100개");
        assertThat(saved.getOrderedFishAmount()).isEqualTo(100);
        assertThat(members.findById(member.getMemberId())
                .orElseThrow().getFishBalance()).isEqualTo(100);
    }

    @Test
    void availableProductsCanBeListedAndViewed() throws Exception {
        mvc.perform(get("/api/v1/products")
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result[*].productCode")
                        .value(org.hamcrest.Matchers.hasItem(
                                fishProduct.getProductCode()
                        )));

        mvc.perform(get("/api/v1/products/{productId}",
                        fishProduct.getProductId())
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.productCode")
                        .value(fishProduct.getProductCode()))
                .andExpect(jsonPath("$.result.price").value(1000));
    }

    @Test
    void paymentStatusCanOnlyBeViewedByOwner() throws Exception {
        PaymentOrderResponse order = createOrder();
        Member other = members.saveAndFlush(
                Member.create(
                        900000L + SEQUENCE.incrementAndGet(),
                        "other" + SEQUENCE.incrementAndGet()
                )
        );

        mvc.perform(get("/api/v1/payments/orders/{orderNumber}",
                        order.orderNumber())
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("READY"));

        mvc.perform(get("/api/v1/payments/orders/{orderNumber}",
                        order.orderNumber())
                        .with(authentication(authToken(other.getMemberId()))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/payments/orders/{orderNumber}",
                        order.orderNumber()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paymentHistoryRequiresAuthenticationAndReturnsOnlyOwnersPayments() throws Exception {
        PaymentOrderResponse first = createOrder();
        PaymentOrderResponse second = createOrder();

        Member other = members.saveAndFlush(
                Member.create(
                        910000L + SEQUENCE.incrementAndGet(),
                        "h" + SEQUENCE.incrementAndGet()
                )
        );
        orderService.createOrder(
                other.getMemberId(),
                fishProduct.getProductCode()
        );

        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/payments")
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.payments.length()").value(2))
                .andExpect(jsonPath("$.result.payments[0].orderNumber")
                        .value(second.orderNumber()))
                .andExpect(jsonPath("$.result.payments[1].orderNumber")
                        .value(first.orderNumber()));
    }

    @Test
    void paymentHistorySupportsStablePagination() throws Exception {
        PaymentOrderResponse first = createOrder();
        PaymentOrderResponse second = createOrder();

        mvc.perform(get("/api/v1/payments")
                        .param("page", "0")
                        .param("size", "1")
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.payments[0].orderNumber")
                        .value(second.orderNumber()));

        mvc.perform(get("/api/v1/payments")
                        .param("page", "1")
                        .param("size", "1")
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.payments[0].orderNumber")
                        .value(first.orderNumber()));
    }

    @Test
    void paymentDetailReturnsOrderSnapshotAndOnlyOwnerCanViewIt() throws Exception {
        PaymentOrderResponse order = createOrder();
        Payment payment = payments.findByOrderNumber(order.orderNumber())
                .orElseThrow();
        Member other = members.saveAndFlush(
                Member.create(
                        920000L + SEQUENCE.incrementAndGet(),
                        "d" + SEQUENCE.incrementAndGet()
                )
        );

        jdbcTemplate.update(
                "UPDATE product SET name = ?, price = ? WHERE product_id = ?",
                "변경된 상품명",
                2000,
                fishProduct.getProductId()
        );

        mvc.perform(get("/api/v1/payments/{paymentId}", payment.getPaymentId())
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.paymentId").value(payment.getPaymentId()))
                .andExpect(jsonPath("$.result.orderNumber").value(order.orderNumber()))
                .andExpect(jsonPath("$.result.productCode")
                        .value(fishProduct.getProductCode()))
                .andExpect(jsonPath("$.result.productName").value("생선 100개"))
                .andExpect(jsonPath("$.result.amount").value(1000))
                .andExpect(jsonPath("$.result.status").value("READY"));

        mvc.perform(get("/api/v1/payments/{paymentId}", payment.getPaymentId())
                        .with(authentication(authToken(other.getMemberId()))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/payments/{paymentId}", payment.getPaymentId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paymentDetailRejectsInvalidOrMissingPaymentId() throws Exception {
        mvc.perform(get("/api/v1/payments/0")
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/payments/{paymentId}", Long.MAX_VALUE)
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidProductDefinitionIsRejectedBeforeSale() {
        assertThatThrownBy(() -> Product.create(
                "INVALID_FISH",
                ProductType.FISH,
                "생선 상품",
                500,
                null,
                null,
                null,
                null,
                true
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Product.create(
                "INVALID_PASS",
                ProductType.PASS,
                "일주일 패스",
                1900,
                null,
                null,
                null,
                null,
                true
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void callbackNeedsNeitherLoginNorCsrfAndDuplicateDoesNotGrantTwice() throws Exception {
        PaymentOrderResponse order = createOrder();
        String paymentKey = "payment-key-" + SEQUENCE.incrementAndGet();
        when(korpayClient.confirm(paymentKey)).thenReturn(
                approvedResponse(order, paymentKey)
        );

        performCallback(order, paymentKey)
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        "http://localhost:3000/payment/success?orderNumber=" + order.orderNumber()
                ));

        performCallback(order, paymentKey)
                .andExpect(status().isFound());

        Payment saved = payments.findByOrderNumber(order.orderNumber()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(members.findById(member.getMemberId()).orElseThrow().getFishBalance())
                .isEqualTo(100);
        verify(korpayClient).confirm(paymentKey);
    }

    @Test
    void callbackDoesNotCallConfirmAgainWhileConfirmationIsInProgress() throws Exception {
        PaymentOrderResponse order = createOrder();
        String paymentKey = "confirming-key-" + SEQUENCE.incrementAndGet();
        Payment payment = payments.findByOrderNumber(order.orderNumber()).orElseThrow();
        payment.startConfirmation(
                paymentKey,
                "testmid",
                LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul"))
        );
        payments.saveAndFlush(payment);

        performCallback(order, paymentKey)
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString("code=PAYMENT_409_5")
                ));

        Payment saved = payments.findById(payment.getPaymentId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.CONFIRMING);
        verify(korpayClient, org.mockito.Mockito.never()).confirm(any());
    }

    @Test
    void staleConfirmationIsChangedToUnknownForReconciliation() {
        PaymentOrderResponse order = createOrder();
        String paymentKey = "stale-confirming-key-" + SEQUENCE.incrementAndGet();
        Payment payment = payments.findByOrderNumber(order.orderNumber()).orElseThrow();
        payment.startConfirmation(
                paymentKey,
                "testmid",
                LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul"))
                        .minusMinutes(11)
        );
        payments.saveAndFlush(payment);

        processingService.markStaleConfirmationsUnknown();

        Payment saved = payments.findById(payment.getPaymentId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(saved.getFailureMessage()).contains("PG 거래 확인");
        verify(korpayClient, org.mockito.Mockito.never()).confirm(any());
    }

    @Test
    void unknownApprovalIsNotGrantedOrAutomaticallyConfirmedAgain() throws Exception {
        PaymentOrderResponse order = createOrder();
        String paymentKey = "unknown-key-" + SEQUENCE.incrementAndGet();
        when(korpayClient.confirm(paymentKey)).thenReturn(
                new KorpayConfirmResponse(
                        "3004", "처리 중", null, "testmid",
                        order.orderNumber(), order.productName(), "KRW",
                        order.amount(), null, "card", null
                )
        );

        performCallback(order, paymentKey)
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString(
                                "/payment/failure?orderNumber=" + order.orderNumber()
                        )
                ));

        performCallback(order, paymentKey)
                .andExpect(status().isFound());

        Payment saved = payments.findByOrderNumber(order.orderNumber()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(members.findById(member.getMemberId()).orElseThrow().getFishBalance())
                .isZero();
        verify(korpayClient).confirm(paymentKey);
    }

    @Test
    void authenticationFailureIsStoredWithoutCallingConfirmApi() throws Exception {
        PaymentOrderResponse order = createOrder();

        mvc.perform(post("/api/v1/payments/callback")
                        .contentType("application/x-www-form-urlencoded")
                        .param("resultCode", "A001")
                        .param("message", "사용자 취소")
                        .param("merchantId", "testmid")
                        .param("orderNumber", order.orderNumber())
                        .param("amount", order.amount().toString()))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString(
                                "code=PAYMENT_400_9"
                        )
                ));

        Payment saved = payments.findByOrderNumber(
                order.orderNumber()
        ).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(korpayClient, org.mockito.Mockito.never()).confirm(any());
    }

    @Test
    void invalidCallbackFormAlwaysRedirectsToFailurePage() throws Exception {
        PaymentOrderResponse order = createOrder();

        mvc.perform(post("/api/v1/payments/callback")
                        .contentType("application/x-www-form-urlencoded")
                        .param("resultCode", "A001")
                        .param("message", "사용자 취소")
                        .param("merchantId", "testmid")
                        .param("orderNumber", order.orderNumber())
                        .param("reserved", order.reserved()))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString(
                                "code=PAYMENT_400_3"
                        )
                ));

        mvc.perform(post("/api/v1/payments/callback")
                        .contentType("application/x-www-form-urlencoded")
                        .param("resultCode", "0000")
                        .param("paymentKey", "invalid-amount-key")
                        .param("merchantId", "testmid")
                        .param("orderNumber", order.orderNumber())
                        .param("amount", "not-a-number")
                        .param("reserved", order.reserved()))
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString(
                                "code=PAYMENT_400_3"
                        )
                ));
    }

    @Test
    void expiredAuthenticationSessionIsNotConfirmed() throws Exception {
        PaymentOrderResponse order = createOrder();
        Payment payment = payments.findByOrderNumber(
                order.orderNumber()
        ).orElseThrow();
        ReflectionTestUtils.setField(
                payment,
                "requestedAt",
                LocalDateTime.ofInstant(NOW, ZoneId.of("Asia/Seoul"))
                        .minusMinutes(31)
        );
        payments.saveAndFlush(payment);

        performCallback(
                order,
                "expired-key-" + SEQUENCE.incrementAndGet()
        )
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString(
                                "code=PAYMENT_410_1"
                        )
                ));

        verify(korpayClient, org.mockito.Mockito.never()).confirm(any());
    }

    @Test
    void mismatchedApprovalResponseIsNotGranted() throws Exception {
        PaymentOrderResponse order = createOrder();
        String paymentKey = "mismatch-key-" + SEQUENCE.incrementAndGet();
        KorpayConfirmResponse approved = approvedResponse(order, paymentKey);

        when(korpayClient.confirm(paymentKey)).thenReturn(
                new KorpayConfirmResponse(
                        approved.resultCode(),
                        approved.message(),
                        approved.tid(),
                        approved.merchantId(),
                        approved.orderNumber(),
                        approved.productName(),
                        approved.currency(),
                        approved.amount() + 1,
                        approved.approvedAt(),
                        approved.payMethod(),
                        approved.reserved()
                )
        );

        performCallback(order, paymentKey)
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString(
                                "code=PAYMENT_400_7"
                        )
                ));

        Payment saved = payments.findByOrderNumber(
                order.orderNumber()
        ).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(members.findById(member.getMemberId())
                .orElseThrow().getFishBalance()).isZero();
    }

    @Test
    void failedFulfillmentLeavesApprovedAndCanBeRetriedWithoutReapproval() throws Exception {
        PaymentOrderResponse order = createOrder();
        String paymentKey = "retry-key-" + SEQUENCE.incrementAndGet();
        when(korpayClient.confirm(paymentKey)).thenReturn(
                approvedResponse(order, paymentKey)
        );
        doThrow(new ProjectException(PaymentErrorCode.FULFILLMENT_FAILED))
                .doCallRealMethod()
                .when(fulfillmentService)
                .fulfill(any(), any());

        performCallback(order, paymentKey)
                .andExpect(status().isFound())
                .andExpect(header().string(
                        "Location",
                        org.hamcrest.Matchers.containsString("/payment/failure")
                ));

        Payment approved = payments.findByOrderNumber(order.orderNumber()).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(approved.getFailureMessage()).isNotBlank();

        doCallRealMethod().when(fulfillmentService).fulfill(any(), any());
        processingService.retryPendingFulfillments();

        Payment completed = payments.findById(approved.getPaymentId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(completed.getFailureMessage()).isNull();
        assertThat(members.findById(member.getMemberId()).orElseThrow().getFishBalance())
                .isEqualTo(100);
        verify(korpayClient).confirm(paymentKey);
    }

    @Test
    void passIsIssuedOnceAndCannotBePurchasedAgain() {
        long sequence = SEQUENCE.incrementAndGet();
        Product passProduct = products.saveAndFlush(
                Product.create(
                        "PASS" + sequence,
                        ProductType.PASS,
                        "일주일 패스",
                        1900,
                        null,
                        168,
                        null,
                        null,
                        true
                )
        );
        PaymentOrderResponse order = orderService.createOrder(
                member.getMemberId(),
                passProduct.getProductCode()
        );
        jdbcTemplate.update(
                "UPDATE product SET pass_duration_hours = ? WHERE product_id = ?",
                336,
                passProduct.getProductId()
        );
        String paymentKey = "pass-key-" + sequence;
        Payment payment = payments.findByOrderNumber(order.orderNumber()).orElseThrow();
        payment.startConfirmation(
                paymentKey,
                "testmid",
                LocalDateTime.now()
        );
        payments.saveAndFlush(payment);

        processingService.processApprovedPayment(
                paymentKey,
                approvedResponse(order, paymentKey)
        );

        assertThat(memberPasses.existsByMemberMemberId(member.getMemberId())).isTrue();
        var memberPass = memberPasses.findAll().stream()
                .filter(pass -> pass.getMember().getMemberId()
                        .equals(member.getMemberId()))
                .findFirst()
                .orElseThrow();
        assertThat(memberPass.getExpiresAt())
                .isEqualTo(memberPass.getStartedAt().plusHours(168));
        assertThatThrownBy(() -> orderService.createOrder(
                member.getMemberId(),
                passProduct.getProductCode()
        )).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(PaymentErrorCode.PASS_ALREADY_PURCHASED)
        );
    }

    private PaymentOrderResponse createOrder() {
        return orderService.createOrder(
                member.getMemberId(),
                fishProduct.getProductCode()
        );
    }

    private KorpayConfirmResponse approvedResponse(
            PaymentOrderResponse order,
            String paymentKey
    ) {
        return new KorpayConfirmResponse(
                "3001",
                "성공",
                "tid-" + paymentKey,
                "testmid",
                order.orderNumber(),
                order.productName(),
                "KRW",
                order.amount(),
                "20261005153000",
                "card",
                null
        );
    }

    private org.springframework.test.web.servlet.ResultActions performCallback(
            PaymentOrderResponse order,
            String paymentKey
    ) throws Exception {
        return mvc.perform(post("/api/v1/payments/callback")
                .contentType("application/x-www-form-urlencoded")
                .param("resultCode", "0000")
                .param("message", "성공")
                .param("paymentKey", paymentKey)
                        .param("merchantId", "testmid")
                        .param("orderNumber", order.orderNumber())
                        .param("amount", order.amount().toString())
                        .param("reserved", order.reserved()));
    }

    private UsernamePasswordAuthenticationToken authToken(Long memberId) {
        return new UsernamePasswordAuthenticationToken(
                memberId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
        }
    }
}
