package demoday.backend.streak.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.service.StockService;
import demoday.backend.streak.code.StreakErrorCode;
import demoday.backend.streak.code.StreakRecoveryMethod;
import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.repository.StreakRecoveryEventRepository;
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
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streak-notification;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(StreakPenaltyNotificationIntegrationTest.TimeConfig.class)
class StreakPenaltyNotificationIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private StreakPenaltyNotificationService notifications;
    @Autowired private StreakPenaltyService penalties;
    @Autowired private StreakRecoveryService recoveries;
    @Autowired private StreakRecoveryEventRepository events;
    @Autowired private MemberRepository members;
    @Autowired private StockService stocks;
    @Autowired private TransactionRetryExecutor transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    private Long memberId;

    @BeforeEach
    void setUp() {
        clock.set("2026-10-05T03:00:00Z"); // KST 12시
        long sequence = SEQUENCE.incrementAndGet();
        memberId = members.saveAndFlush(Member.create(sequence, "n" + sequence)).getMemberId();
    }

    private Long createPenalty() {
        transactions.execute(() -> {
            var member = members.findByIdForUpdate(memberId).orElseThrow();
            member.completeLearning(false, TODAY.minusDays(4));
            member.completeLearning(true, TODAY.minusDays(3));
            member.completeLearning(true, TODAY.minusDays(2));
            return null;
        });
        penalties.applyDuePenalty(memberId);
        return events.findFirstByMemberMemberIdOrderByMissedDateDesc(memberId).orElseThrow().getStreakRecoveryEventId();
    }

    private Long createNextPenalty() {
        transactions.execute(() -> {
            members.findByIdForUpdate(memberId).orElseThrow().completeLearning(false, TODAY);
            return null;
        });
        clock.set("2026-10-07T03:00:00Z");
        penalties.applyDuePenalty(memberId);
        return events.findFirstByMemberMemberIdOrderByMissedDateDesc(memberId).orElseThrow().getStreakRecoveryEventId();
    }

    @Test
    void noPenaltyReturnsNullAndRepeatedReadsDoNotConsumeNotification() {
        assertThat(notifications.getLatestUnread(memberId)).isNull();
        Long eventId = createPenalty();
        var first = notifications.getLatestUnread(memberId);
        assertThat(first.eventId()).isEqualTo(eventId);
        assertThat(first.missedDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(first.stockBeforePenalty()).isEqualByComparingTo("100");
        assertThat(first.stockAfterPenalty()).isEqualByComparingTo("80");
        assertThat(first.decreaseAmount()).isEqualByComparingTo("20");
        assertThat(first.streakBeforePenalty()).isEqualTo(3);
        assertThat(notifications.getLatestUnread(memberId)).isEqualTo(first);
        assertThat(events.findById(eventId).orElseThrow().getPenaltyNotificationAcknowledgedAt()).isNull();
    }

    @Test
    void confirmationHidesNotificationAndReplayPreservesFirstTimestamp() {
        Long eventId = createPenalty();
        var first = notifications.acknowledge(memberId, eventId);
        assertThat(first.acknowledgedAt()).isEqualTo(TODAY.atTime(12, 0));
        clock.set("2026-10-05T05:00:00Z");
        assertThat(notifications.acknowledge(memberId, eventId)).isEqualTo(first);
        assertThat(notifications.getLatestUnread(memberId)).isNull();
        var member = members.findById(memberId).orElseThrow();
        assertThat(member.getCurrentStock()).isEqualByComparingTo("80");
        assertThat(member.getCurrentStreak()).isZero();
        assertThat(member.getFishBalance()).isZero();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(StreakRecoveryStatus.AVAILABLE);
        assertThat(jdbc.queryForObject("select count(*) from stock_change where member_id=?", Integer.class, memberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from member_item where member_id=?", Integer.class, memberId)).isZero();
    }

    @Test
    void lateConfirmationOfOlderEventDoesNotHideNewEventAndOlderUnreadIsNotQueued() {
        Long oldId = createPenalty();
        Long newId = createNextPenalty();
        assertThat(notifications.getLatestUnread(memberId).eventId()).isEqualTo(newId);
        notifications.acknowledge(memberId, oldId);
        assertThat(notifications.getLatestUnread(memberId).eventId()).isEqualTo(newId);
        notifications.acknowledge(memberId, newId);
        assertThat(notifications.getLatestUnread(memberId)).isNull();
    }

    @Test
    void confirmingLatestDoesNotExposeAnOlderUnreadEvent() {
        Long oldId = createPenalty();
        Long newId = createNextPenalty();
        notifications.acknowledge(memberId, newId);
        assertThat(events.findById(oldId).orElseThrow().getPenaltyNotificationAcknowledgedAt()).isNull();
        assertThat(notifications.getLatestUnread(memberId)).isNull();
    }

    @Test
    void payloadKeepsPenaltyValuesEvenWhenCurrentStockChanges() {
        createPenalty();
        stocks.changeStock(memberId, new BigDecimal("80"), new BigDecimal("88"),
                StockChangeType.QUIZ_CORRECT, 1L, "notification-gain:" + memberId);
        assertThat(notifications.getLatestUnread(memberId).stockAfterPenalty()).isEqualByComparingTo("80");
        assertThat(stocks.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("88");
    }

    @Test
    void expiredPendingStatusIsComputedWithoutChangingRecoveryOrReversingConsumption() {
        Long eventId = createPenalty();
        transactions.execute(() -> {
            members.findByIdForUpdate(memberId).orElseThrow();
            events.findById(eventId).orElseThrow().requestRecovery(StreakRecoveryMethod.PASS, TODAY.atTime(12, 0), null, null);
            return null;
        });
        clock.set("2026-10-05T15:00:00Z");
        assertThat(notifications.getLatestUnread(memberId).recoveryStatus()).isEqualTo(StreakRecoveryStatus.EXPIRED);
        assertThat(recoveries.getLatest(memberId).status()).isEqualTo(StreakRecoveryStatus.EXPIRED);
        notifications.acknowledge(memberId, eventId);
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(StreakRecoveryStatus.PENDING);
    }

    @Test
    void simultaneousConfirmationsStoreOneStableTimestamp() throws Exception {
        Long eventId = createPenalty();
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return notifications.acknowledge(memberId, eventId); });
            var second = pool.submit(() -> { start.await(); return notifications.acknowledge(memberId, eventId); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThat(notifications.getLatestUnread(memberId)).isNull();
    }

    @Test
    void httpRequiresSessionRoleCsrfAndOwnEvent() throws Exception {
        Long eventId = createPenalty();
        var auth = new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(get("/api/v1/streaks/penalty-notification")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/streaks/penalty-notification").with(authentication(
                new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/streaks/penalty-notification").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.eventId").value(eventId));
        mvc.perform(post("/api/v1/streaks/penalty-notifications/{id}/acknowledgment", eventId).with(authentication(auth)))
                .andExpect(status().isForbidden());
        long sequence = SEQUENCE.incrementAndGet();
        Long otherId = members.saveAndFlush(Member.create(sequence, "n" + sequence)).getMemberId();
        var otherAuth = new UsernamePasswordAuthenticationToken(otherId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(post("/api/v1/streaks/penalty-notifications/{id}/acknowledgment", eventId)
                        .with(authentication(otherAuth)).with(csrf()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STREAK_404_2"));
        mvc.perform(post("/api/v1/streaks/penalty-notifications/{id}/acknowledgment", eventId)
                        .with(authentication(auth)).with(csrf()).param("memberId", otherId.toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/streaks/penalty-notification").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result").isEmpty());
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", memberId);
        mvc.perform(get("/api/v1/streaks/penalty-notification").with(authentication(auth))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/streaks/penalty-notifications/{id}/acknowledgment", eventId)
                .with(authentication(auth)).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/streaks/penalty-notification'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/streaks/penalty-notifications/{eventId}/acknowledgment'].post").exists());
    }

    @Test
    void missingEventAndInvalidIdDoNotAcknowledgeAnything() {
        createPenalty();
        assertThatThrownBy(() -> notifications.acknowledge(memberId, Long.MAX_VALUE)).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(StreakErrorCode.PENALTY_NOTIFICATION_NOT_FOUND));
        assertThatThrownBy(() -> notifications.acknowledge(memberId, 0L)).isInstanceOf(ProjectException.class);
        assertThat(notifications.getLatestUnread(memberId)).isNotNull();
    }

    static class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;
        MutableClock() { this(new AtomicReference<>(Instant.parse("2026-10-05T03:00:00Z")), ZoneOffset.UTC); }
        private MutableClock(AtomicReference<Instant> instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        void set(String value) { instant.set(Instant.parse(value)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant.get(); }
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary MutableClock notificationClock() { return new MutableClock(); }
    }
}
