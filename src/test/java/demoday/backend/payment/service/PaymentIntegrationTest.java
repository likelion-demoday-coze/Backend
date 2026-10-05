package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
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

        mvc.perform(get("/api/v1/payments/{orderNumber}",
                        order.orderNumber())
                        .with(authentication(authToken(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("READY"));

        mvc.perform(get("/api/v1/payments/{orderNumber}",
                        order.orderNumber())
                        .with(authentication(authToken(other.getMemberId()))))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/payments/{orderNumber}",
                        order.orderNumber()))
                .andExpect(status().isUnauthorized());
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
        String paymentKey = "pass-key-" + sequence;
        Payment payment = payments.findByOrderNumber(order.orderNumber()).orElseThrow();
        payment.recordAuthentication(paymentKey, "testmid");
        payments.saveAndFlush(payment);

        processingService.processApprovedPayment(
                paymentKey,
                approvedResponse(order, paymentKey)
        );

        assertThat(memberPasses.existsByMemberMemberId(member.getMemberId())).isTrue();
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
                .param("amount", order.amount().toString()));
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
