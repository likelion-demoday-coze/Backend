package demoday.backend.streak.service;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.support.StockChangeReadHook;
import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.repository.StreakRecoveryEventRepository;
import demoday.backend.streak.scheduler.StreakPenaltyScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streak-penalty;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@Import(StreakPenaltyIntegrationTest.TimeConfig.class)
class StreakPenaltyIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private StreakPenaltyService penalties;
    @Autowired private StreakPenaltyScheduler scheduler;
    @Autowired private MemberRepository members;
    @Autowired private DailyQuizSessionRepository sessions;
    @Autowired private DailyQuizSessionQuestionRepository sessionQuestions;
    @Autowired private QuizQuestionRepository questions;
    @Autowired private QuizOptionRepository options;
    @Autowired private DailyQuizService quizzes;
    @Autowired private StreakRecoveryEventRepository events;
    @Autowired private StockChangeRepository changes;
    @Autowired private TransactionRetryExecutor transactions;
    @Autowired private JdbcTemplate jdbc;
    private Long memberId;

    @BeforeEach
    void setUp() {
        long sequence = SEQUENCE.incrementAndGet();
        memberId = members.saveAndFlush(Member.create(sequence, "p" + sequence)).getMemberId();
    }

    private void learnedThreeDaysEnding(LocalDate date) {
        transactions.execute(() -> {
            var member = members.findByIdForUpdate(memberId).orElseThrow();
            member.completeLearning(false, date.minusDays(2));
            member.completeLearning(true, date.minusDays(1));
            member.completeLearning(true, date);
            return null;
        });
    }

    @Test
    void firstMissedDayDropsTwentyPercentAndPreservesRecoveryEvidence() {
        learnedThreeDaysEnding(TODAY.minusDays(2));
        assertThat(penalties.applyDuePenalty(memberId)).isTrue();
        var saved = members.findById(memberId).orElseThrow();
        assertThat(saved.getCurrentStock()).isEqualByComparingTo("80.00");
        assertThat(saved.getCurrentStreak()).isZero();
        assertThat(saved.getLastLearningDate()).isEqualTo(TODAY.minusDays(2));
        var event = events.findAll().stream().filter(e -> e.getMember().getMemberId().equals(memberId))
                .findFirst().orElseThrow();
        assertThat(event.getMissedDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(event.getStreakBeforePenalty()).isEqualTo(3);
        assertThat(event.getStockBeforePenalty()).isEqualByComparingTo("100.00");
        assertThat(event.getStockAfterPenalty()).isEqualByComparingTo("80.00");
        assertThat(event.getPenaltyAppliedAt()).isEqualTo(TODAY.atStartOfDay());
        assertThat(event.getStatus()).isEqualTo(StreakRecoveryStatus.AVAILABLE);
        var change = changes.findById(event.getPenaltyStockChangeId()).orElseThrow();
        assertThat(change.getReferenceId()).isEqualTo(event.getStreakRecoveryEventId());
    }

    @Test
    void tenMissedDaysAndRepeatedSchedulerDoNotMultiplyPenalty() {
        learnedThreeDaysEnding(TODAY.minusDays(11));
        scheduler.applyAtMidnight();
        scheduler.applyOnStartup();
        assertThat(penalties.applyDuePenalty(memberId)).isFalse();
        assertThat(members.findById(memberId).orElseThrow().getCurrentStock()).isEqualByComparingTo("80.00");
        assertEventAndChangeCounts(1);
        assertThat(jdbc.queryForObject("select missed_date from streak_recovery_event where member_id=?",
                LocalDate.class, memberId)).isEqualTo(TODAY.minusDays(10));
    }

    @Test
    void yesterdayAndNewMembersAndWithdrawnMembersAreNotPenalized() {
        assertThat(penalties.applyDuePenalty(memberId)).isFalse();
        learnedThreeDaysEnding(TODAY.minusDays(1));
        assertThat(penalties.applyDuePenalty(memberId)).isFalse();
        jdbc.update("update member set last_learning_date=?, status='WITHDRAWN' where member_id=?",
                TODAY.minusDays(2), memberId);
        assertThat(penalties.applyDuePenalty(memberId)).isFalse();
        assertEventAndChangeCounts(0);
    }

    @Test
    void nextStreakCanHaveItsOwnSinglePenalty() {
        learnedThreeDaysEnding(TODAY.minusDays(5));
        penalties.applyDuePenalty(memberId);
        transactions.execute(() -> {
            members.findByIdForUpdate(memberId).orElseThrow().completeLearning(false, TODAY.minusDays(2));
            return null;
        });
        assertThat(penalties.applyDuePenalty(memberId)).isTrue();
        assertThat(members.findById(memberId).orElseThrow().getCurrentStock()).isEqualByComparingTo("64.00");
        assertEventAndChangeCounts(2);
    }

    @Test
    void legacyCompletedRegularQuizCountsButPartialQuizDoesNot() {
        transactions.execute(() -> {
            var member = members.findByIdForUpdate(memberId).orElseThrow();
            var session = DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                    member.getCurrentStock(), TODAY.minusDays(2).atTime(10, 0), false);
            session.completeOriginal(member.getCurrentStock());
            session.expire(TODAY.atStartOfDay());
            sessions.save(session);
            sessions.save(DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                    member.getCurrentStock(), TODAY.minusDays(1).atTime(10, 0), false));
            return null;
        });
        jdbc.update("update member set current_streak=3 where member_id=?", memberId);
        assertThat(penalties.applyDuePenalty(memberId)).isTrue();
        assertEventAndChangeCounts(1);
    }

    @Test
    void roundingUsesTwoDecimalPlaces() {
        learnedThreeDaysEnding(TODAY.minusDays(2));
        jdbc.update("update member set current_stock=100.03 where member_id=?", memberId);
        penalties.applyDuePenalty(memberId);
        assertThat(members.findById(memberId).orElseThrow().getCurrentStock()).isEqualByComparingTo("80.02");
    }

    @Test
    void lockFailureRollsBackEventAndRetriesEntirePenalty() {
        learnedThreeDaysEnding(TODAY.minusDays(2));
        AtomicInteger attempts = new AtomicInteger();
        try (var hook = StockChangeReadHook.install(changes, result -> {
            if (attempts.incrementAndGet() == 1) throw new CannotAcquireLockException("첫 처리 실패");
            return result;
        })) {
            assertThat(penalties.applyDuePenalty(memberId)).isTrue();
        }
        assertThat(attempts.get()).isEqualTo(2);
        assertEventAndChangeCounts(1);
    }

    @Test
    void exhaustedRetriesLeaveStockStreakAndEventsUnchanged() {
        learnedThreeDaysEnding(TODAY.minusDays(2));
        try (var hook = StockChangeReadHook.install(changes, result -> {
            throw new CannotAcquireLockException("지속적인 잠금 실패");
        })) {
            assertThatThrownBy(() -> penalties.applyDuePenalty(memberId)).isInstanceOf(CannotAcquireLockException.class);
        }
        var saved = members.findById(memberId).orElseThrow();
        assertThat(saved.getCurrentStock()).isEqualByComparingTo("100.00");
        assertThat(saved.getCurrentStreak()).isEqualTo(3);
        assertEventAndChangeCounts(0);
    }

    @Test
    void callerFailureAfterPenaltyRollsBackStockEventAndStreakTogether() {
        learnedThreeDaysEnding(TODAY.minusDays(2));
        assertThatThrownBy(() -> transactions.execute(() -> {
            penalties.applyDuePenaltyInTransaction(memberId, TODAY);
            throw new IllegalStateException("후속 퀴즈 작업 실패");
        })).isInstanceOf(IllegalStateException.class);
        var saved = members.findById(memberId).orElseThrow();
        assertThat(saved.getCurrentStock()).isEqualByComparingTo("100.00");
        assertThat(saved.getCurrentStreak()).isEqualTo(3);
        assertEventAndChangeCounts(0);
        assertThat(penalties.applyDuePenalty(memberId)).isTrue();
    }

    @Test
    void simultaneousWorkersProduceOnlyOnePenalty() throws Exception {
        learnedThreeDaysEnding(TODAY.minusDays(2));
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> { start.await(); return penalties.applyDuePenalty(memberId); });
            var second = pool.submit(() -> { start.await(); return penalties.applyDuePenalty(memberId); });
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS) ^ second.get(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }
        assertEventAndChangeCounts(1);
        assertThat(members.findById(memberId).orElseThrow().getCurrentStock()).isEqualByComparingTo("80.00");
    }

    @Test
    void quizStartAppliesDelayedPenaltyBeforeRecordingStartStock() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        learnedThreeDaysEnding(today.minusDays(2));
        jdbc.update("update member set fish_balance=100 where member_id=?", memberId);
        for (int index = 0; index < 5; index++) {
            questions.saveAndFlush(QuizQuestion.create(QuizCategory.MACRO_ECONOMY,
                    "MULTIPLE_CHOICE", "시작 문제" + index, "해설", true));
        }
        quizzes.createSession(memberId, new DailyQuizSessionCreateRequest(QuizCategory.MACRO_ECONOMY));
        assertThat(sessions.findFirstByMemberMemberIdAndStatusInOrderByStartedAtDesc(memberId,
                java.util.List.of(demoday.backend.dailyquiz.code.DailyQuizSessionStatus.IN_PROGRESS))
                .orElseThrow().getStartStock()).isEqualByComparingTo("80.00");
        assertEventAndChangeCounts(1);
    }

    @Test
    void originalAnswersCatchUpPenaltyBeforeRestartingStreak() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        learnedThreeDaysEnding(now.toLocalDate().minusDays(2));
        var member = members.findById(memberId).orElseThrow();
        var session = sessions.saveAndFlush(DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(), now, false));
        for (int index = 0; index < 5; index++) {
            var question = questions.saveAndFlush(QuizQuestion.create(QuizCategory.MACRO_ECONOMY,
                    "MULTIPLE_CHOICE", "답안 문제" + index, "해설", true));
            var wrong = options.saveAndFlush(QuizOption.create(question, 1, "오답", false));
            options.saveAndFlush(QuizOption.create(question, 2, "정답", true));
            var sq = sessionQuestions.saveAndFlush(DailyQuizSessionQuestion.create(question, session));
            quizzes.submitAnswer(memberId, session.getDailyQuizSessionId(), sq.getSessionQuestionId(),
                    new DailyQuizAnswerRequest(wrong.getOptionId(), DailyQuizAttemptType.ORIGINAL));
        }
        var saved = members.findById(memberId).orElseThrow();
        assertThat(saved.getCurrentStock()).isEqualByComparingTo("80.00");
        assertThat(saved.getCurrentStreak()).isEqualTo(1);
        assertThat(saved.getLastLearningDate()).isEqualTo(now.toLocalDate());
        assertEventAndChangeCounts(1);
    }

    private void assertEventAndChangeCounts(int expected) {
        assertThat(jdbc.queryForObject("select count(*) from streak_recovery_event where member_id=?",
                Integer.class, memberId)).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select count(*) from stock_change where member_id=?",
                Integer.class, memberId)).isEqualTo(expected);
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary
        Clock penaltyClock() {
            return Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC);
        }
    }
}
