package demoday.backend.fish.service;

import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.fish.code.FishErrorCode;
import demoday.backend.fish.dto.FishTransactionResponse;
import demoday.backend.fish.repository.FishTransactionRepository;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static demoday.backend.fish.code.FishTransactionType.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fish-retry-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class FishTransactionRetryIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    @MockitoSpyBean private FishService fishService;
    @MockitoSpyBean private FishTransactionRepository fishTransactionRepository;
    @Autowired private DailyQuizService dailyQuizService;
    @Autowired private TransactionRetryExecutor retryExecutor;
    @Autowired private MemberRepository memberRepository;
    @Autowired private QuizQuestionRepository quizQuestionRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mockMvc;
    private Long memberId;

    @BeforeEach
    void setUp() {
        long sequence = SEQUENCE.incrementAndGet();
        memberId = memberRepository.saveAndFlush(Member.create(sequence, "retry" + sequence)).getMemberId();
        fishService.credit(memberId, 100, ATTENDANCE_REWARD, null, "seed:" + memberId);
        quizQuestionRepository.saveAll(IntStream.range(0, 5).mapToObj(index ->
                QuizQuestion.create(QuizCategory.MACRO_ECONOMY, "MULTIPLE_CHOICE", "문제", "해설", true)).toList());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rollsBackWholeQuizAndRetriesWithFreshPersistenceContext(boolean legacyDeadlock) {
        List<Object> contexts = new ArrayList<>();
        List<Long> sessionIds = new ArrayList<>();
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            contexts.add(EntityManagerFactoryUtils.getTransactionalEntityManager(entityManagerFactory));
            sessionIds.add(invocation.getArgument(3));
            FishTransactionResponse response = (FishTransactionResponse) invocation.callRealMethod();
            if (attempts.incrementAndGet() == 1) {
                throw legacyDeadlock
                        ? new DeadlockLoserDataAccessException("simulated deadlock after insert", null)
                        : new CannotAcquireLockException("simulated lock failure after insert");
            }
            return response;
        }).when(fishService).debit(eq(memberId), eq(50L), eq(DAILY_QUIZ_COST), anyLong(), anyString());

        var result = dailyQuizService.createSession(memberId,
                new DailyQuizSessionCreateRequest(QuizCategory.MACRO_ECONOMY));

        assertThat(attempts).hasValue(2);
        assertThat(contexts).doesNotContainNull();
        assertThat(contexts.get(0)).isNotSameAs(contexts.get(1));
        assertThat(result.sessionId()).isEqualTo(sessionIds.get(1));
        assertThat(jdbc.queryForObject("select count(*) from daily_quiz_session where daily_quiz_session_id = ?",
                Long.class, sessionIds.get(0))).isZero();
        assertThat(jdbc.queryForObject("select count(*) from daily_quiz_session_question where daily_quiz_session_id = ?",
                Long.class, sessionIds.get(0))).isZero();
        assertWallet(50, 2, 1);
    }

    @Test
    void convertsActualUniqueViolationWithoutRetryingAndRollsBackCallerWrites() {
        String key = "seed:" + memberId;
        // 사전 조회 직후 다른 요청이 키를 선점한 상황을 재현한다. INSERT의 UNIQUE 검사는 실제 DB가 수행한다.
        doReturn(Optional.empty()).when(fishTransactionRepository).findByIdempotencyKey(key);
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            jdbc.update("update member set current_streak = 7 where member_id = ?", memberId);
            return fishService.credit(memberId, 200, ATTENDANCE_REWARD, null, key);
        })).isInstanceOfSatisfying(ProjectException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(FishErrorCode.IDEMPOTENCY_CONFLICT));

        assertThat(attempts).hasValue(1);
        assertThat(jdbc.queryForObject("select current_streak from member where member_id = ?",
                Integer.class, memberId)).isZero();
        assertWallet(100, 1, 0);
    }

    @Test
    void doesNotRelabelOtherIntegrityFailuresAsIdempotencyConflicts() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("not a unique violation");
        doThrow(failure).when(fishTransactionRepository).save(any());
        AtomicInteger attempts = new AtomicInteger();

        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            attempts.incrementAndGet();
            return fishService.credit(memberId, 100, ATTENDANCE_REWARD, null, "other:" + memberId);
        })).isSameAs(failure);
        assertThat(attempts).hasValue(1);
        assertWallet(100, 1, 0);
    }

    @Test
    void exhaustedRetriesReturn503AndLeaveNoPartialQuiz() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            attempts.incrementAndGet();
            throw new CannotAcquireLockException("still busy");
        }).when(fishService).debit(eq(memberId), eq(50L), eq(DAILY_QUIZ_COST), anyLong(), anyString());

        mockMvc.perform(post("/api/v1/daily-quizzes/sessions")
                        .with(authentication(new UsernamePasswordAuthenticationToken(memberId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")))))
                        .with(csrf()).contentType("application/json").content("{\"category\":\"MACRO_ECONOMY\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.code").value("COMMON_503"));

        assertThat(attempts).hasValue(3);
        assertWallet(100, 1, 0);
    }

    private void assertWallet(long balance, long transactions, long sessions) {
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(balance);
        assertThat(fishService.getTransactions(memberId, 0, 20).totalElements()).isEqualTo(transactions);
        assertThat(jdbc.queryForObject("select count(*) from daily_quiz_session where member_id = ?",
                Long.class, memberId)).isEqualTo(sessions);
    }
}
