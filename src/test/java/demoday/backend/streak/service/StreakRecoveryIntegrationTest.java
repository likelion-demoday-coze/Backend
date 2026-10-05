package demoday.backend.streak.service;

import demoday.backend.activity.domain.MemberDailyActivity;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.*;
import demoday.backend.payment.domain.*;
import demoday.backend.payment.repository.*;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.service.StockService;
import demoday.backend.stock.support.StockChangeReadHook;
import demoday.backend.store.domain.MemberItem;
import demoday.backend.store.domain.StoreItem;
import demoday.backend.store.repository.MemberItemRepository;
import demoday.backend.store.repository.StoreItemRepository;
import demoday.backend.streak.code.StreakErrorCode;
import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.repository.StreakRecoveryEventRepository;
import demoday.backend.streak.scheduler.StreakPenaltyScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streak-recovery;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(StreakRecoveryIntegrationTest.TimeConfig.class)
class StreakRecoveryIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private StreakRecoveryService recoveries;
    @Autowired private StreakPenaltyService penalties;
    @Autowired private StreakPenaltyScheduler scheduler;
    @Autowired private MemberRepository members;
    @Autowired private MemberItemRepository inventory;
    @Autowired private StoreItemRepository items;
    @Autowired private StreakRecoveryEventRepository events;
    @Autowired private MemberDailyActivityRepository activities;
    @Autowired private DailyQuizService quizzes;
    @Autowired private DailyQuizSessionRepository sessions;
    @Autowired private DailyQuizSessionQuestionRepository sessionQuestions;
    @Autowired private QuizQuestionRepository questions;
    @Autowired private QuizOptionRepository options;
    @Autowired private MemberPassRepository passes;
    @Autowired private ProductRepository products;
    @Autowired private PaymentRepository payments;
    @Autowired private StockService stocks;
    @Autowired private StockChangeRepository changes;
    @Autowired private TransactionRetryExecutor transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private MutableClock clock;
    private Long memberId;
    private StoreItem item;

    @BeforeEach
    void setUp() {
        clock.set("2026-10-05T03:00:00Z"); // KST 12시
        long id = SEQUENCE.incrementAndGet();
        memberId = members.saveAndFlush(Member.create(id, "r" + id)).getMemberId();
        item = items.findAll().stream().filter(i -> i.getItemCode().equals("STREAK_RECOVERY"))
                .findFirst().orElseGet(() -> items.saveAndFlush(StoreItem.create("STREAK_RECOVERY", "복구권", 200, true, "복구")));
    }

    private Long breakStreak(int missedDays) {
        transactions.execute(() -> {
            var member = members.findByIdForUpdate(memberId).orElseThrow();
            LocalDate last = TODAY.minusDays(missedDays + 1L);
            for (int day = 2; day >= 0; day--) {
                LocalDate date = last.minusDays(day);
                member.completeLearning(day != 2, date);
                var activity = MemberDailyActivity.create(member, date);
                activity.completeLearning(date.atTime(12, 0));
                activities.save(activity);
            }
            return null;
        });
        penalties.applyDuePenalty(memberId);
        return events.findFirstByMemberMemberIdOrderByMissedDateDesc(memberId).orElseThrow().getStreakRecoveryEventId();
    }

    private void giveItems(int quantity) {
        inventory.saveAndFlush(MemberItem.create(members.findById(memberId).orElseThrow(), item, quantity));
    }

    private void completeRegularQuiz() {
        boolean pending = recoveries.getLatest(memberId).status() == StreakRecoveryStatus.PENDING;
        var member = members.findById(memberId).orElseThrow();
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        var session = sessions.saveAndFlush(DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(), now, false));
        for (int index = 0; index < 5; index++) {
            var question = questions.saveAndFlush(QuizQuestion.create(QuizCategory.MACRO_ECONOMY,
                    "MULTIPLE_CHOICE", "복구 문제" + index, "해설", true));
            var wrong = options.saveAndFlush(QuizOption.create(question, 1, "오답", false));
            options.saveAndFlush(QuizOption.create(question, 2, "정답", true));
            var sq = sessionQuestions.saveAndFlush(DailyQuizSessionQuestion.create(question, session));
            quizzes.submitAnswer(memberId, session.getDailyQuizSessionId(), sq.getSessionQuestionId(),
                    new DailyQuizAnswerRequest(wrong.getOptionId(), DailyQuizAttemptType.ORIGINAL));
            if (pending && index < 4) assertThat(recoveries.getLatest(memberId).status()).isEqualTo(StreakRecoveryStatus.PENDING);
        }
    }

    @Test
    void requestConsumesOneThenFifthAnswerRestoresStockAndFiveDayStreak() {
        Long eventId = breakStreak(1);
        giveItems(2);
        assertThat(recoveries.request(memberId, eventId).status()).isEqualTo(StreakRecoveryStatus.PENDING);
        assertThat(quantity()).isEqualTo(1);
        assertThat(member().getCurrentStock()).isEqualByComparingTo("80");
        stocks.changeStock(memberId, new BigDecimal("80"), new BigDecimal("88"),
                StockChangeType.QUIZ_CORRECT, 1L, "gain:" + memberId);
        completeRegularQuiz();
        var result = recoveries.getLatest(memberId);
        assertThat(result.status()).isEqualTo(StreakRecoveryStatus.RECOVERED);
        assertThat(result.stockAfterRecovery()).isEqualByComparingTo("110");
        assertThat(result.streakAfterRecovery()).isEqualTo(5);
        assertThat(recoveries.request(memberId, eventId)).isEqualTo(result);
        assertThat(quantity()).isEqualTo(1);
        assertThat(recoveryCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select learning_status from member_daily_activity where member_id=? and activity_date=?",
                String.class, memberId, TODAY.minusDays(1))).isEqualTo("RECOVERED");
        clock.set("2026-10-06T03:00:00Z");
        completeRegularQuiz();
        assertThat(member().getCurrentStreak()).isEqualTo(6);
        assertThat(recoveryCount()).isEqualTo(1);
    }

    @Test
    void quizFirstThenRequestImmediatelyRecoversAndDuplicateDoesNotConsumeAgain() {
        Long eventId = breakStreak(1);
        giveItems(2);
        completeRegularQuiz();
        assertThat(member().getCurrentStreak()).isEqualTo(1);
        var result = recoveries.request(memberId, eventId);
        assertThat(result.streakAfterRecovery()).isEqualTo(5);
        assertThat(result.status()).isEqualTo(StreakRecoveryStatus.RECOVERED);
        assertThat(recoveries.request(memberId, eventId)).isEqualTo(result);
        assertThat(quantity()).isEqualTo(1);
    }

    @Test
    void twoMissedDaysRestartAtTwoAndTenDaysAreAlsoRecoverable() {
        Long eventId = breakStreak(2);
        giveItems(1);
        recoveries.request(memberId, eventId);
        completeRegularQuiz();
        assertThat(member().getCurrentStreak()).isEqualTo(2);
    }

    @Test
    void longAbsenceHasNoRequestDeadline() {
        Long eventId = breakStreak(10);
        giveItems(1);
        recoveries.request(memberId, eventId);
        completeRegularQuiz();
        assertThat(member().getCurrentStreak()).isEqualTo(2);
        assertThat(member().getCurrentStock()).isEqualByComparingTo("100");
    }

    @Test
    void passUsesNoInventoryAndExpiryAfterRequestDoesNotRevokeGrantedRecovery() {
        Long eventId = breakStreak(1);
        giveItems(2);
        createPass(LocalDateTime.of(TODAY, LocalTime.of(13, 0)));
        assertThat(recoveries.request(memberId, eventId).method().name()).isEqualTo("PASS");
        assertThat(quantity()).isEqualTo(2);
        clock.set("2026-10-05T05:00:00Z"); // KST 14시, 패스는 만료됨
        completeRegularQuiz();
        assertThat(member().getCurrentStock()).isEqualByComparingTo("100");
        assertThat(quantity()).isEqualTo(2);
    }

    @Test
    void passDoesNotNeedAnInventoryRow() {
        Long eventId = breakStreak(1);
        createPass(TODAY.plusDays(1).atStartOfDay());
        assertThat(recoveries.request(memberId, eventId).quantityAfterRequest()).isNull();
        completeRegularQuiz();
        assertThat(recoveries.getLatest(memberId).status()).isEqualTo(StreakRecoveryStatus.RECOVERED);
    }

    @Test
    void expiredPassAndZeroInventoryCannotRequest() {
        Long eventId = breakStreak(1);
        giveItems(0);
        createPass(TODAY.atTime(12, 0)); // 정확히 만료 시각
        assertThatThrownBy(() -> recoveries.request(memberId, eventId)).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(StreakErrorCode.NO_RECOVERY_ITEM));
        assertThat(recoveries.getLatest(memberId).status()).isEqualTo(StreakRecoveryStatus.AVAILABLE);
    }

    @Test
    void midnightExpiresWithoutRefundAndNextDayQuizDoesNotRecover() {
        Long eventId = breakStreak(1);
        giveItems(1);
        recoveries.request(memberId, eventId);
        clock.set("2026-10-05T15:00:00Z"); // 다음 날 00:00 KST
        assertThat(recoveries.getLatest(memberId).status()).isEqualTo(StreakRecoveryStatus.EXPIRED);
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(StreakRecoveryStatus.PENDING);
        scheduler.applyAtMidnight();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(StreakRecoveryStatus.EXPIRED);
        assertThat(recoveries.request(memberId, eventId).status()).isEqualTo(StreakRecoveryStatus.EXPIRED);
        completeRegularQuiz();
        assertThat(member().getCurrentStreak()).isEqualTo(1);
        assertThat(member().getCurrentStock()).isEqualByComparingTo("80");
        assertThat(quantity()).isZero();
        assertThat(recoveryCount()).isZero();
    }

    @Test
    void yesterdayRestartedLearningMakesOldEventUnavailable() {
        Long eventId = breakStreak(3);
        giveItems(1);
        transactions.execute(() -> {
            members.findByIdForUpdate(memberId).orElseThrow().completeLearning(false, TODAY.minusDays(1));
            return null;
        });
        assertThatThrownBy(() -> recoveries.request(memberId, eventId)).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(StreakErrorCode.STALE_RECOVERY));
        assertThat(recoveries.getLatest(memberId).requestable()).isFalse();
        assertThat(quantity()).isEqualTo(1);
    }

    @Test
    void yesterdayRestartAndTodayCompletionRejectOldRecoveryWithoutChangingState() throws Exception {
        Long eventId = breakStreak(3);
        giveItems(1);
        clock.set("2026-10-04T03:00:00Z");
        completeRegularQuiz();
        clock.set("2026-10-05T03:00:00Z");
        completeRegularQuiz();
        assertThat(member().getCurrentStreak()).isEqualTo(2);
        assertThat(member().getLastLearningDate()).isEqualTo(TODAY);
        var auth = new UsernamePasswordAuthenticationToken(memberId, null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(get("/api/v1/streaks/recovery").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.requestable").value(false));
        mvc.perform(post("/api/v1/streaks/recoveries/{id}", eventId).with(authentication(auth)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STREAK_409_3"));
        assertThat(quantity()).isEqualTo(1);
        assertThat(member().getCurrentStock()).isEqualByComparingTo("80");
        assertThat(member().getCurrentStreak()).isEqualTo(2);
        assertThat(recoveryCount()).isZero();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(StreakRecoveryStatus.AVAILABLE);
        assertThat(jdbc.queryForObject("select learning_status from member_daily_activity where member_id=? and activity_date=?",
                String.class, memberId, TODAY.minusDays(1))).isEqualTo("COMPLETED");
    }

    @Test
    void simultaneousDuplicateRequestsConsumeOnlyOne() throws Exception {
        Long eventId = breakStreak(1);
        giveItems(2);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return recoveries.request(memberId, eventId); });
            var second = pool.submit(() -> { start.await(); return recoveries.request(memberId, eventId); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertThat(quantity()).isEqualTo(1);
    }

    @Test
    void anotherEventCannotConsumeASecondItemOnTheSameDay() {
        Long eventId = breakStreak(2);
        giveItems(2);
        recoveries.request(memberId, eventId);
        Long anotherEventId = transactions.execute(() -> events.saveAndFlush(
                demoday.backend.streak.domain.StreakRecoveryEvent.create(
                        members.findByIdForUpdate(memberId).orElseThrow(), TODAY.minusDays(1), 1,
                        new BigDecimal("80"), new BigDecimal("64"), TODAY.atTime(12, 0)))
                .getStreakRecoveryEventId());
        assertThatThrownBy(() -> recoveries.request(memberId, anotherEventId)).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(StreakErrorCode.DAILY_RECOVERY_LIMIT));
        assertThat(quantity()).isEqualTo(1);
    }

    @Test
    void immediateRecoveryLockFailureRetriesRequestWithoutDoubleConsumption() {
        Long eventId = breakStreak(1);
        giveItems(2);
        completeRegularQuiz();
        AtomicInteger attempts = new AtomicInteger();
        try (var hook = StockChangeReadHook.install(changes, result -> {
            if (attempts.incrementAndGet() == 1) throw new CannotAcquireLockException("첫 복구 잠금 실패");
            return result;
        })) {
            assertThat(recoveries.request(memberId, eventId).status()).isEqualTo(StreakRecoveryStatus.RECOVERED);
        }
        assertThat(attempts.get()).isEqualTo(2);
        assertThat(quantity()).isEqualTo(1);
        assertThat(recoveryCount()).isEqualTo(1);
    }

    @Test
    void failedImmediateRecoveryRollsBackConsumptionRequestAndStock() {
        Long eventId = breakStreak(1);
        giveItems(1);
        completeRegularQuiz();
        try (var hook = StockChangeReadHook.install(changes, result -> {
            throw new CannotAcquireLockException("지속적인 복구 실패");
        })) {
            assertThatThrownBy(() -> recoveries.request(memberId, eventId)).isInstanceOf(CannotAcquireLockException.class);
        }
        assertThat(quantity()).isEqualTo(1);
        assertThat(recoveries.getLatest(memberId).status()).isEqualTo(StreakRecoveryStatus.AVAILABLE);
        assertThat(member().getCurrentStock()).isEqualByComparingTo("80");
        assertThat(member().getCurrentStreak()).isEqualTo(1);
        assertThat(recoveryCount()).isZero();
    }

    @Test
    void laterQuizFailureRollsBackRecoveryButDoesNotRefundPreviouslyConsumedItem() {
        Long eventId = breakStreak(1);
        giveItems(1);
        recoveries.request(memberId, eventId);
        assertThatThrownBy(() -> transactions.execute(() -> {
            members.findByIdForUpdate(memberId).orElseThrow().completeLearning(false, TODAY);
            recoveries.completeForLearningInTransaction(memberId, TODAY.atTime(12, 0));
            throw new IllegalStateException("복구 이후 퀴즈 작업 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(quantity()).isZero();
        assertThat(member().getCurrentStock()).isEqualByComparingTo("80");
        assertThat(member().getCurrentStreak()).isZero();
        assertThat(recoveries.getLatest(memberId).status()).isEqualTo(StreakRecoveryStatus.PENDING);
        assertThat(recoveryCount()).isZero();
        completeRegularQuiz();
        assertThat(member().getCurrentStreak()).isEqualTo(5);
    }

    @Test
    void httpScopesEventToPrincipalAndRequiresRoleAndCsrf() throws Exception {
        Long eventId = breakStreak(1);
        giveItems(1);
        var auth = new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(get("/api/v1/streaks/recovery")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/streaks/recovery").with(authentication(
                new UsernamePasswordAuthenticationToken(memberId, null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))))))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/streaks/recoveries/{id}", eventId).with(authentication(auth)))
                .andExpect(status().isForbidden());
        long sequence = SEQUENCE.incrementAndGet();
        Long otherId = members.saveAndFlush(Member.create(sequence, "r" + sequence)).getMemberId();
        var other = new UsernamePasswordAuthenticationToken(otherId, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(post("/api/v1/streaks/recoveries/{id}", eventId).with(authentication(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/streaks/recoveries/{id}", eventId).with(authentication(auth)).with(csrf())
                        .param("memberId", otherId.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("PENDING"));
        assertThat(quantity()).isZero();
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", memberId);
        mvc.perform(get("/api/v1/streaks/recovery").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/streaks/recoveries/{eventId}'].post").exists());
    }

    private Member member() { return members.findById(memberId).orElseThrow(); }
    private int quantity() { return inventory.findByMemberMemberIdAndItemItemId(memberId, item.getItemId()).orElseThrow().getQuantity(); }
    private int recoveryCount() { return jdbc.queryForObject("select count(*) from stock_change where member_id=? and change_type='STREAK_RECOVERY'", Integer.class, memberId); }

    private void createPass(LocalDateTime expiresAt) {
        var product = products.saveAndFlush(Product.create(UUID.randomUUID().toString(), ProductType.PASS,
                "패스", 1900, null, 168, null, null, true));
        var payment = payments.saveAndFlush(Payment.create(member(), product, UUID.randomUUID().toString(),
                1900, PaymentStatus.APPROVED, TODAY.atStartOfDay()));
        passes.saveAndFlush(MemberPass.create(member(), payment, PassType.SEVEN_DAY, TODAY.atStartOfDay(), expiresAt));
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
        @Bean @Primary MutableClock recoveryClock() { return new MutableClock(); }
    }
}
