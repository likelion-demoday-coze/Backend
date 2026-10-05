package demoday.backend.attendance.service;

import demoday.backend.attendance.code.AttendanceErrorCode;
import demoday.backend.attendance.code.AttendanceRewardStatus;
import demoday.backend.attendance.dto.AttendanceRewardClaimResponse;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.*;
import demoday.backend.payment.domain.*;
import demoday.backend.payment.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:attendance;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(AttendanceRewardIntegrationTest.TimeConfig.class)
class AttendanceRewardIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private AttendanceRewardService service;
    @Autowired private MemberRepository members;
    @Autowired private ProductRepository products;
    @Autowired private PaymentRepository payments;
    @Autowired private MemberPassRepository passes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    @MockitoSpyBean private FishService fish;
    private Member member;
    private Long memberId;

    @BeforeEach
    void setUp() {
        clock.set("2026-09-29T15:00:00Z"); // UTC는 29일, KST는 30일 00:00
        long id = SEQUENCE.incrementAndGet();
        member = members.saveAndFlush(Member.create(id, "att" + id));
        memberId = member.getMemberId();
    }

    @Test
    void statusDoesNotGrantReward() throws Exception {
        mvc.perform(get("/api/v1/attendance-rewards/today").with(auth(memberId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.date").value("2026-09-30"))
                .andExpect(jsonPath("$.result.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.result.rewardAmount").value(100));
        assertUnchanged();
    }

    @Test
    void claimAndRepeatAfterSpendingDoNotGrantAgain() throws Exception {
        mvc.perform(post("/api/v1/attendance-rewards").with(auth(memberId)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.newlyClaimed").value(true))
                .andExpect(jsonPath("$.result.grantedAmount").value(100))
                .andExpect(jsonPath("$.result.balance").value(100));
        fish.debit(memberId, 20, FishTransactionType.ITEM_PURCHASE, null, "spend:" + memberId);
        var replay = service.claim(memberId);
        assertThat(replay.newlyClaimed()).isFalse();
        assertThat(replay.grantedAmount()).isZero();
        assertThat(replay.balance()).isEqualTo(80);
        assertThat(service.getToday(memberId).status()).isEqualTo(AttendanceRewardStatus.CLAIMED);
        Member saved = members.findById(memberId).orElseThrow();
        assertThat(saved.getLastAttendanceRewardDate()).isEqualTo(LocalDate.of(2026, 9, 30));
        assertThat(saved.getCurrentStreak()).isZero();
        assertThat(saved.getCurrentStock()).isEqualByComparingTo("100");
        assertThat(rewardCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from member_daily_activity where member_id=?", Long.class, memberId)).isZero();
        assertThat(jdbc.queryForObject("select idempotency_key from fish_transaction where member_id=? and transaction_type='ATTENDANCE_REWARD'",
                String.class, memberId)).isEqualTo("ATTENDANCE_REWARD:" + memberId + ":2026-09-30");
    }

    @Test
    void midnightAllowsNewRewardWithoutScheduler() {
        clock.set("2026-09-29T14:59:59Z");
        assertThat(service.claim(memberId).rewardDate()).isEqualTo(LocalDate.of(2026, 9, 29));
        clock.set("2026-09-29T15:00:00Z");
        assertThat(service.getToday(memberId).status()).isEqualTo(AttendanceRewardStatus.AVAILABLE);
        assertThat(service.claim(memberId).balance()).isEqualTo(200);
        assertThat(rewardCount()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({"-1,1,ACTIVE,true", "0,1,ACTIVE,true", "-1,0,ACTIVE,false", "-2,-1,ACTIVE,false",
            "1,2,ACTIVE,false", "-1,1,CANCELLED,false", "-1,1,EXPIRED,false"})
    void passStatusAndExactExpiryBoundary(int startHours, int endHours, PassStatus passStatus, boolean blocked) {
        createPass(member, startHours, endHours, passStatus);
        assertThat(service.getToday(memberId).status()).isEqualTo(blocked ? AttendanceRewardStatus.PASS_ACTIVE : AttendanceRewardStatus.AVAILABLE);
        if (blocked) {
            assertThatThrownBy(() -> service.claim(memberId)).isInstanceOfSatisfying(ProjectException.class,
                    ex -> assertThat(ex.getErrorCode()).isEqualTo(AttendanceErrorCode.ACTIVE_PASS));
            assertUnchanged();
        } else {
            assertThat(service.claim(memberId).newlyClaimed()).isTrue();
        }
    }

    @Test
    void rewardBeforePassIsNotRevokedAndDuplicateRemainsNoOp() {
        service.claim(memberId);
        createPass(member, 0, 168, PassStatus.ACTIVE);
        assertThat(service.getToday(memberId).status()).isEqualTo(AttendanceRewardStatus.PASS_ACTIVE);
        assertThat(service.claim(memberId).newlyClaimed()).isFalse();
        assertThat(fish.getBalance(memberId).balance()).isEqualTo(100);
        assertThat(rewardCount()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    void concurrentRequestsGrantOnce() throws Exception {
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<AttendanceRewardClaimResponse> operation = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return service.claim(memberId);
            };
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            var results = List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertThat(results.stream().filter(AttendanceRewardClaimResponse::newlyClaimed).count()).isEqualTo(1);
            assertThat(fish.getBalance(memberId).balance()).isEqualTo(100);
            assertThat(rewardCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void failureAfterCreditRollsBackBalanceHistoryAndDate() {
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("지급 이후 실패");
        }).when(fish).credit(eq(memberId), anyLong(), any(), any(), anyString());
        assertThatThrownBy(() -> service.claim(memberId)).isInstanceOf(IllegalStateException.class);
        assertUnchanged();
    }

    @Test
    void lockFailureRetriesWholeOperation() {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) throw new CannotAcquireLockException("잠금 충돌");
            return result;
        }).when(fish).credit(eq(memberId), anyLong(), any(), any(), anyString());
        assertThat(service.claim(memberId).balance()).isEqualTo(100);
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(rewardCount()).isEqualTo(1);
    }

    @Test
    void exhaustedLockRetriesReturn503AndLeaveNothing() throws Exception {
        doThrow(new CannotAcquireLockException("잠금 충돌"))
                .when(fish).credit(eq(memberId), anyLong(), any(), any(), anyString());
        mvc.perform(post("/api/v1/attendance-rewards").with(auth(memberId)).with(csrf()))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After", "1"));
        verify(fish, times(3)).credit(eq(memberId), anyLong(), any(), any(), anyString());
        assertUnchanged();
    }

    @Test
    void overflowingBalanceDoesNotMarkClaimed() {
        jdbc.update("update member set fish_balance=? where member_id=?", Long.MAX_VALUE, memberId);
        assertThatThrownBy(() -> service.claim(memberId)).isInstanceOf(ProjectException.class);
        assertThat(members.findById(memberId).orElseThrow().getLastAttendanceRewardDate()).isNull();
        assertThat(rewardCount()).isZero();
    }

    @Test
    void authCsrfAndPrincipalIsolation() throws Exception {
        mvc.perform(get("/api/v1/attendance-rewards/today")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/attendance-rewards").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/attendance-rewards").with(auth(memberId))).andExpect(status().isForbidden());
        var guest = authentication(new UsernamePasswordAuthenticationToken(memberId, null,
                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))));
        mvc.perform(post("/api/v1/attendance-rewards").with(guest).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/attendance-rewards").param("memberId", Long.MAX_VALUE + "")
                        .with(auth(memberId)).with(csrf())).andExpect(status().isOk());
        assertThat(rewardCount()).isEqualTo(1);
    }

    @Test
    void missingAndWithdrawnMembersAreRejected() throws Exception {
        assertThatThrownBy(() -> service.claim(null)).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(GeneralErrorCode.UNAUTHORIZED));
        mvc.perform(post("/api/v1/attendance-rewards").with(auth(Long.MAX_VALUE)).with(csrf()))
                .andExpect(status().isNotFound());
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", memberId);
        mvc.perform(get("/api/v1/attendance-rewards/today").with(auth(memberId))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/attendance-rewards").with(auth(memberId)).with(csrf())).andExpect(status().isForbidden());
        assertThat(rewardCount()).isZero();
    }

    @Test
    void swaggerHasBothOperations() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/attendance-rewards/today'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/attendance-rewards'].post").exists());
    }

    private void createPass(Member owner, int startHours, int endHours, PassStatus status) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        Product product = products.saveAndFlush(Product.create(UUID.randomUUID().toString(), ProductType.PASS,
                "일주일 패스", 1900, null, 168, null, null, true));
        Payment payment = payments.saveAndFlush(Payment.create(owner, product, UUID.randomUUID().toString(),
                1900, PaymentStatus.APPROVED, now));
        MemberPass pass = passes.saveAndFlush(MemberPass.create(owner, payment, PassType.SEVEN_DAY,
                now.plusHours(startHours), now.plusHours(endHours)));
        jdbc.update("update member_pass set status=? where member_pass_id=?", status.name(), pass.getMemberPassId());
    }
    private long rewardCount() {
        return jdbc.queryForObject("select count(*) from fish_transaction where member_id=? and transaction_type='ATTENDANCE_REWARD'", Long.class, memberId);
    }
    private void assertUnchanged() {
        Member saved = members.findById(memberId).orElseThrow();
        assertThat(saved.getFishBalance()).isZero();
        assertThat(saved.getLastAttendanceRewardDate()).isNull();
        assertThat(rewardCount()).isZero();
    }
    private RequestPostProcessor auth(Long id) {
        return authentication(new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;
        // 서버 시작 작업도 시계를 사용하므로 @BeforeEach 이전부터 유효한 시각을 제공한다.
        MutableClock() { this(new AtomicReference<>(Instant.parse("2026-09-29T15:00:00Z")), ZoneOffset.UTC); }
        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        void set(String value) { instant.set(Instant.parse(value)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant.get(); }
    }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock attendanceClock() { return new MutableClock(); }
    }
}
