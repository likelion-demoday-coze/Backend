package demoday.backend.store.service;

import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.*;
import demoday.backend.payment.domain.*;
import demoday.backend.payment.repository.*;
import demoday.backend.store.code.StoreErrorCode;
import demoday.backend.store.domain.*;
import demoday.backend.store.dto.*;
import demoday.backend.store.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.http.MediaType;

import java.util.*;
import java.time.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storepurchase;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class StorePurchaseIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private StorePurchaseService service;
    @Autowired private MemberRepository members;
    @Autowired private StoreItemRepository items;
    @MockitoSpyBean private MemberItemRepository inventory;
    @MockitoSpyBean private FishService fish;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private ProductRepository products;
    @Autowired private PaymentRepository payments;
    @Autowired private MemberPassRepository passes;
    @Autowired private Clock clock;
    private Long memberId;
    private StoreItem item;

    @BeforeEach
    void setUp() {
        long sequence = SEQUENCE.incrementAndGet();
        memberId = members.saveAndFlush(Member.create(50000L + sequence, "buy" + sequence)).getMemberId();
        item = items.saveAndFlush(StoreItem.create("RECOVERY" + sequence, "복구권", 200, true, "아이템 사용 안내"));
        fish.credit(memberId, 1000, FishTransactionType.ADMIN_ADJUSTMENT, null, "setup:" + memberId);
    }

    @Test
    void purchasesAndReplaysOriginalResultAfterPriceAndInventoryChanges() {
        StorePurchaseRequest request = request(2);
        var original = service.purchase(memberId, item.getItemId(), request);
        assertThat(original.unitPrice()).isEqualTo(200);
        assertThat(original.totalPrice()).isEqualTo(400);
        assertThat(original.balanceAfter()).isEqualTo(600);
        assertThat(original.quantityAfter()).isEqualTo(2);
        assertThat(quantity()).isEqualTo(2);
        var second = service.purchase(memberId, item.getItemId(), request(1));
        assertThat(second.quantityAfter()).isEqualTo(3);
        jdbc.update("update store_item set fish_price=300, active=false where item_id=?", item.getItemId());
        assertThat(service.purchase(memberId, item.getItemId(), request)).isEqualTo(original);
        assertThat(fish.getBalance(memberId).balance()).isEqualTo(400);
        assertThat(quantity()).isEqualTo(3);
        assertThat(count("store_purchase")).isEqualTo(2);
        assertThat(purchaseTransactionCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select reference_id from fish_transaction where member_id=? and idempotency_key=?",
                Long.class, memberId, "STORE_PURCHASE:" + memberId + ":" + request.requestId()))
                .isEqualTo(original.purchaseId());
    }

    @Test
    void sameRequestIdWithDifferentQuantityOrItemConflicts() {
        StorePurchaseRequest request = request(1);
        service.purchase(memberId, item.getItemId(), request);
        assertThatThrownBy(() -> service.purchase(memberId, item.getItemId(),
                new StorePurchaseRequest(2, request.requestId())))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StoreErrorCode.PURCHASE_CONFLICT));
        StoreItem another = items.saveAndFlush(StoreItem.create("OTHER" + memberId, "다른 상품", 200, true, "아이템 사용 안내"));
        assertThatThrownBy(() -> service.purchase(memberId, another.getItemId(), request))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StoreErrorCode.PURCHASE_CONFLICT));
        assertThat(quantity()).isEqualTo(1);
        assertThat(purchaseTransactionCount()).isEqualTo(1);
    }

    @Test
    void sameRequestIdCanBeUsedByDifferentMembers() {
        var request = request(1);
        Long otherId = members.saveAndFlush(Member.create(90000L + memberId, "otherbuy")).getMemberId();
        fish.credit(otherId, 200, FishTransactionType.ADMIN_ADJUSTMENT, null, "setup:" + otherId);
        assertThat(service.purchase(memberId, item.getItemId(), request).purchaseId())
                .isNotEqualTo(service.purchase(otherId, item.getItemId(), request).purchaseId());
    }

    @Test
    void activePassStillPaysActualFish() {
        Member member = members.findById(memberId).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        Product product = products.saveAndFlush(Product.create(UUID.randomUUID().toString(), ProductType.PASS,
                "일주일 패스", 1900, null, 168, null, null, true));
        Payment payment = Payment.create(member, product, UUID.randomUUID().toString().replace("-", ""),
                1900, now.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")), now);
        payment.startConfirmation(
                UUID.randomUUID().toString(),
                "testmid",
                LocalDateTime.now()
        );
        payment.approve(UUID.randomUUID().toString(), "3001", "card", now);
        payments.saveAndFlush(payment);
        passes.saveAndFlush(MemberPass.create(member, payment, PassType.SEVEN_DAY, now.minusHours(1), now.plusHours(167)));
        var result = service.purchase(memberId, item.getItemId(), request(1));
        assertThat(result.totalPrice()).isEqualTo(200);
        assertThat(fish.getBalance(memberId).balance()).isEqualTo(800);
        assertThat(quantity()).isEqualTo(1);
    }

    @Test
    void insufficientFundsAndUnavailableItemsDoNotChangeAnything() {
        assertThatThrownBy(() -> service.purchase(memberId, item.getItemId(), request(6)))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.INSUFFICIENT_FISH));
        jdbc.update("update store_item set active=false where item_id=?", item.getItemId());
        assertThatThrownBy(() -> service.purchase(memberId, item.getItemId(), request(1)))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StoreErrorCode.ITEM_UNAVAILABLE));
        assertThatThrownBy(() -> service.purchase(memberId, Long.MAX_VALUE, request(1)))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StoreErrorCode.ITEM_NOT_FOUND));
        assertUnchanged();
    }

    @Test
    void quantityOverflowAndHugeCostDoNotWrapAround() {
        inventory.saveAndFlush(MemberItem.create(members.findById(memberId).orElseThrow(), item, Integer.MAX_VALUE));
        assertThatThrownBy(() -> service.purchase(memberId, item.getItemId(), request(1)))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StoreErrorCode.QUANTITY_OVERFLOW));
        assertThat(quantity()).isEqualTo(Integer.MAX_VALUE);
        assertThat(purchaseTransactionCount()).isZero();
        StoreItem expensive = items.saveAndFlush(StoreItem.create("EXP" + memberId, "고가", Integer.MAX_VALUE, true, "아이템 사용 안내"));
        assertThatThrownBy(() -> service.purchase(memberId, expensive.getItemId(), request(Integer.MAX_VALUE)))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.INSUFFICIENT_FISH));
        assertThat(fish.getBalance(memberId).balance()).isEqualTo(1000);
    }

    @Test
    void zeroQuantityInventoryIsReusedAndFreeItemsHaveNoDebit() {
        inventory.saveAndFlush(MemberItem.create(members.findById(memberId).orElseThrow(), item, 0));
        jdbc.update("update store_item set fish_price=0 where item_id=?", item.getItemId());
        var request = request(2);
        var result = service.purchase(memberId, item.getItemId(), request);
        assertThat(result.totalPrice()).isZero();
        assertThat(result.balanceAfter()).isEqualTo(1000);
        assertThat(quantity()).isEqualTo(2);
        assertThat(count("member_item")).isEqualTo(1);
        assertThat(purchaseTransactionCount()).isZero();
        assertThat(service.purchase(memberId, item.getItemId(), request)).isEqualTo(result);
        assertThat(quantity()).isEqualTo(2);
    }

    @Test
    void inventoryFailureRollsBackPurchaseAndDebit() {
        doThrow(new IllegalStateException("inventory failure")).when(inventory).save(any(MemberItem.class));
        assertThatThrownBy(() -> service.purchase(memberId, item.getItemId(), request(1)))
                .isInstanceOf(IllegalStateException.class);
        assertUnchanged();
    }

    @Test
    void lockFailureRetriesWholePurchaseInNewTransaction() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) throw new CannotAcquireLockException("simulated deadlock");
            return result;
        }).when(fish).debit(eq(memberId), anyLong(), any(), any(), anyString());
        var result = service.purchase(memberId, item.getItemId(), request(1));
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(result.balanceAfter()).isEqualTo(800);
        assertThat(quantity()).isEqualTo(1);
        assertThat(count("store_purchase")).isEqualTo(1);
        assertThat(purchaseTransactionCount()).isEqualTo(1);
    }

    @Test
    void exhaustedLockRetriesReturn503AndRollBackEveryAttempt() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("simulated persistent deadlock");
        }).when(fish).debit(eq(memberId), anyLong(), any(), any(), anyString());
        mvc.perform(post("/api/v1/store/items/" + item.getItemId() + "/purchase")
                        .with(auth(memberId, "ROLE_MEMBER")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1,\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"));
        assertThat(attempts.get()).isEqualTo(3);
        assertUnchanged();
    }

    @Test
    @Timeout(30)
    void concurrentSameRequestGrantsOnce() throws Exception {
        var request = request(1);
        List<StorePurchaseResponse> results = concurrently(() -> service.purchase(memberId, item.getItemId(), request));
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(quantity()).isEqualTo(1);
        assertThat(fish.getBalance(memberId).balance()).isEqualTo(800);
        assertThat(purchaseTransactionCount()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    void concurrentDistinctPurchasesCannotOverspend() throws Exception {
        List<Boolean> results = concurrently(() -> {
            try {
                service.purchase(memberId, item.getItemId(), request(3));
                return true;
            } catch (ProjectException ex) {
                assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.INSUFFICIENT_FISH);
                return false;
            }
        });
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(quantity()).isEqualTo(3);
        assertThat(fish.getBalance(memberId).balance()).isEqualTo(400);
        assertThat(purchaseTransactionCount()).isEqualTo(1);
    }

    @Test
    void httpUsesSessionMemberAndRequiresCsrfAndValidBody() throws Exception {
        String body = "{\"quantity\":1,\"requestId\":\"" + UUID.randomUUID() + "\"}";
        String route = "/api/v1/store/items/" + item.getItemId() + "/purchase";
        mvc.perform(post(route).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(route).with(auth(memberId, "ROLE_MEMBER")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post(route).with(auth(memberId, "ROLE_GUEST")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        for (String invalid : List.of("{}", "{\"quantity\":0,\"requestId\":\"" + UUID.randomUUID() + "\"}",
                "{\"quantity\":1,\"requestId\":\"invalid\"}")) {
            mvc.perform(post(route).with(auth(memberId, "ROLE_MEMBER")).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(invalid)).andExpect(status().isBadRequest());
        }
        mvc.perform(post(route).param("memberId", Long.MAX_VALUE + "").with(auth(memberId, "ROLE_MEMBER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.quantityAfter").value(1));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/store/items/{itemId}/purchase'].post").exists());
    }

    @Test
    void missingAndWithdrawnMemberCannotPurchase() throws Exception {
        String body = "{\"quantity\":1,\"requestId\":\"" + UUID.randomUUID() + "\"}";
        String route = "/api/v1/store/items/" + item.getItemId() + "/purchase";
        mvc.perform(post(route).with(auth(Long.MAX_VALUE, "ROLE_MEMBER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", memberId);
        mvc.perform(post(route).with(auth(memberId, "ROLE_MEMBER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("COMMON_401"));
        assertThat(count("store_purchase")).isZero();
    }

    private <T> List<T> concurrently(Callable<T> operation) throws Exception {
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<T> synchronizedOperation = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return operation.call();
            };
            var first = pool.submit(synchronizedOperation);
            var second = pool.submit(synchronizedOperation);
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private StorePurchaseRequest request(int quantity) { return new StorePurchaseRequest(quantity, UUID.randomUUID()); }
    private RequestPostProcessor auth(Long id, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role))));
    }
    private int quantity() {
        return jdbc.queryForObject("select quantity from member_item where member_id=? and item_id=?", Integer.class,
                memberId, item.getItemId());
    }
    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where member_id=?", Long.class, memberId);
    }
    private long purchaseTransactionCount() {
        return jdbc.queryForObject("select count(*) from fish_transaction where member_id=? and transaction_type='ITEM_PURCHASE'",
                Long.class, memberId);
    }
    private void assertUnchanged() {
        assertThat(fish.getBalance(memberId).balance()).isEqualTo(1000);
        assertThat(count("store_purchase")).isZero();
        assertThat(count("member_item")).isZero();
        assertThat(purchaseTransactionCount()).isZero();
    }
}
