package demoday.backend.stock.service;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerResponse;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.support.StockChangeReadHook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-quiz;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
class StockQuizIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private DailyQuizService quizzes;
    @Autowired private StockService stocks;
    @Autowired private MemberRepository members;
    @Autowired private QuizQuestionRepository questions;
    @Autowired private QuizOptionRepository options;
    @Autowired private DailyQuizSessionRepository sessions;
    @Autowired private DailyQuizSessionQuestionRepository sessionQuestions;
    @Autowired private StockChangeRepository changes;
    @Autowired private JdbcTemplate jdbc;
    private Long memberId;
    private Long sessionId;
    private Long sessionQuestionId;
    private Long correctOptionId;
    private Long wrongOptionId;

    @BeforeEach
    void setUp() {
        long sequence = SEQUENCE.incrementAndGet();
        Member member = members.saveAndFlush(Member.create(sequence, "quiz" + sequence));
        memberId = member.getMemberId();
        var question = questions.saveAndFlush(QuizQuestion.create(
                QuizCategory.MACRO_ECONOMY, "MULTIPLE_CHOICE", "문제", "해설", true));
        correctOptionId = options.saveAndFlush(QuizOption.create(question, 1, "정답", true)).getOptionId();
        wrongOptionId = options.saveAndFlush(QuizOption.create(question, 2, "오답", false)).getOptionId();
        var session = sessions.saveAndFlush(DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(), LocalDateTime.now(ZoneId.of("Asia/Seoul")), false));
        sessionId = session.getDailyQuizSessionId();
        sessionQuestionId = sessionQuestions.saveAndFlush(DailyQuizSessionQuestion.create(question, session))
                .getSessionQuestionId();
    }

    @Test
    void quizWritesStockAndHistoryOnceAndReplaysOriginalPercent() {
        var first = submit(correctOptionId);
        assertThat(first.stockIncreasePercent()).isBetween(1, 10);
        assertThat(first.currentStock()).isEqualByComparingTo(BigDecimal.valueOf(100 + first.stockIncreasePercent()));
        assertThat(submit(correctOptionId)).isEqualTo(first);
        var history = stocks.getChanges(memberId, 0, 20);
        assertThat(history.totalElements()).isEqualTo(1);
        assertThat(history.content().get(0).stockAfter()).isEqualByComparingTo(first.currentStock());
        assertThat(history.content().get(0).referenceId()).isEqualTo(jdbc.queryForObject(
                "select daily_quiz_attempt_id from daily_quiz_attempt where session_question_id=?", Long.class, sessionQuestionId));
        assertAnswerCount(1);
    }

    @Test
    void wrongAnswerDoesNotChangeStock() {
        var result = submit(wrongOptionId);
        assertThat(result.stockIncreasePercent()).isNull();
        assertThat(result.currentStock()).isEqualByComparingTo("100");
        assertThat(stocks.getChanges(memberId, 0, 20).content()).isEmpty();
    }

    @Test
    void deadlockRetriesWholeAnswerWithFreshTransaction() {
        AtomicInteger attempts = new AtomicInteger();
        try (var hook = StockChangeReadHook.install(changes, result -> {
            if (attempts.incrementAndGet() == 1) throw new CannotAcquireLockException("교착 상태 재현");
            return result;
        })) {
            var result = submit(correctOptionId);
            assertThat(attempts.get()).isEqualTo(2);
            assertThat(stocks.getCurrentStock(memberId).currentStock()).isEqualByComparingTo(result.currentStock());
        }
        assertAnswerCount(1);
        assertThat(stocks.getChanges(memberId, 0, 20).totalElements()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select original_answer_count from member_daily_activity where member_id=?",
                Integer.class, memberId)).isEqualTo(1);
    }

    @Test
    void exhaustedRetriesRollBackAnswerAndStock() {
        AtomicInteger attempts = new AtomicInteger();
        try (var hook = StockChangeReadHook.install(changes, result -> {
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("지속적인 잠금 실패");
        })) {
            assertThatThrownBy(() -> submit(correctOptionId)).isInstanceOf(CannotAcquireLockException.class);
        }
        assertThat(attempts.get()).isEqualTo(3);
        assertAnswerCount(0);
        assertThat(stocks.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("100");
        assertThat(stocks.getChanges(memberId, 0, 20).content()).isEmpty();
    }

    private DailyQuizAnswerResponse submit(Long optionId) {
        return (DailyQuizAnswerResponse) quizzes.submitAnswer(memberId, sessionId, sessionQuestionId,
                new DailyQuizAnswerRequest(optionId, DailyQuizAttemptType.ORIGINAL));
    }

    private void assertAnswerCount(int expected) {
        assertThat(jdbc.queryForObject("select count(*) from daily_quiz_attempt where session_question_id=?",
                Integer.class, sessionQuestionId)).isEqualTo(expected);
    }
}
