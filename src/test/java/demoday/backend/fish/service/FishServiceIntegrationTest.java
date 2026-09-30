package demoday.backend.fish.service;

import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.fish.code.FishErrorCode;
import demoday.backend.fish.dto.FishTransactionResponse;
import demoday.backend.fish.repository.FishTransactionRepository;
import demoday.backend.fish.support.FishTransactionReadHook;
import demoday.backend.global.api.code.BaseErrorCode;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static demoday.backend.fish.code.FishTransactionType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fish-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(FishServiceIntegrationTest.FixedClockConfig.class)
class FishServiceIntegrationTest {

    private static final AtomicLong MEMBER_SEQUENCE = new AtomicLong();

    @Autowired private FishService fishService;
    @Autowired private FishTransactionRepository fishTransactionRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private DailyQuizService dailyQuizService;
    @Autowired private QuizQuestionRepository quizQuestionRepository;

    private Long memberId;

    @BeforeEach
    void createMember() {
        memberId = newMember().getMemberId();
    }

    @Test
    @DisplayName("신규 회원의 잔액은 0이고 거래 내역은 비어 있다")
    void emptyWallet() {
        assertThat(fishService.getBalance(memberId).balance()).isZero();
        assertThat(fishService.getTransactions(memberId, 0, 20).content()).isEmpty();
    }

    @Test
    @DisplayName("MySQL 테스트의 조회 훅은 실제 Repository 조회를 실행하고 종료 후 제거된다")
    void readHookDelegatesToRealRepositoryAndIsRemoved() {
        List<Boolean> observed = new ArrayList<>();
        FishTransactionResponse original;
        try (var hook = FishTransactionReadHook.install(fishTransactionRepository, result -> {
            observed.add(result.isPresent());
            return result;
        })) {
            original = credit(100, "hook");
            assertThat(credit(100, "hook")).isEqualTo(original);
            assertThat(observed).containsExactly(false, true);
        }

        assertThat(credit(100, "hook")).isEqualTo(original);
        assertThat(observed).containsExactly(false, true);
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(100);
    }

    @Test
    @DisplayName("지급과 차감은 잔액과 부호 있는 이력을 함께 저장하고 KST 시각을 기록한다")
    void creditAndDebit() {
        FishTransactionResponse credit = credit(100, "reward");
        FishTransactionResponse debit = fishService.debit(memberId, 50, DAILY_QUIZ_COST, 1L, key("quiz"));

        assertThat(credit.amount()).isEqualTo(100);
        assertThat(credit.balanceAfter()).isEqualTo(100);
        assertThat(credit.createdAt()).isEqualTo(LocalDateTime.of(2026, 9, 28, 0, 0));
        assertThat(debit.amount()).isEqualTo(-50);
        assertThat(debit.referenceId()).isEqualTo(1L);
        assertThat(debit.balanceAfter()).isEqualTo(50);
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(50);
        assertThat(fishService.getTransactions(memberId, 0, 20).content())
                .containsExactly(debit, credit);
    }

    @Test
    @DisplayName("잔액 부족 시 잔액과 거래 내역이 변하지 않는다")
    void insufficientBalance() {
        credit(30, "reward");
        assertError(() -> fishService.debit(memberId, 50, DAILY_QUIZ_COST, 1L, key("quiz")),
                MemberErrorCode.INSUFFICIENT_FISH);
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(30);
        assertThat(fishService.getTransactions(memberId, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 거래 후 재요청해도 기존 거래 결과를 반환하고 잔액을 다시 변경하지 않는다")
    void idempotentRetry() {
        FishTransactionResponse originalCredit = credit(100, "reward");
        FishTransactionResponse originalDebit = fishService.debit(memberId, 100, ITEM_PURCHASE, 1L, key("purchase"));

        assertThat(credit(100, "reward")).isEqualTo(originalCredit);
        assertThat(fishService.debit(memberId, 100, ITEM_PURCHASE, 1L, key("purchase")))
                .isEqualTo(originalDebit);
        assertThat(fishService.getBalance(memberId).balance()).isZero();
        assertThat(fishService.getTransactions(memberId, 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("같은 키의 금액, 사유, 참조 ID, 방향 또는 회원이 다르면 충돌로 처리한다")
    void rejectChangedCommand() {
        credit(100, "reward");
        assertError(() -> credit(200, "reward"), FishErrorCode.IDEMPOTENCY_CONFLICT);
        assertError(() -> fishService.credit(memberId, 100, RANKING_REWARD, null, key("reward")),
                FishErrorCode.IDEMPOTENCY_CONFLICT);
        assertError(() -> fishService.credit(memberId, 100, ATTENDANCE_REWARD, 1L, key("reward")),
                FishErrorCode.IDEMPOTENCY_CONFLICT);
        assertError(() -> fishService.debit(memberId, 100, ITEM_PURCHASE, null, key("reward")),
                FishErrorCode.IDEMPOTENCY_CONFLICT);
        Long otherId = newMember().getMemberId();
        assertError(() -> fishService.credit(otherId, 100, ATTENDANCE_REWARD, null, key("reward")),
                FishErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(100);
        assertThat(fishService.getBalance(otherId).balance()).isZero();
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    @DisplayName("지급과 차감 수량은 모두 양수여야 한다")
    void invalidAmount(long amount) {
        assertError(() -> credit(amount, "invalid"), MemberErrorCode.INVALID_FISH_AMOUNT);
        assertError(() -> fishService.debit(memberId, amount, ITEM_PURCHASE, 1L, key("invalid")),
                MemberErrorCode.INVALID_FISH_AMOUNT);
        assertThat(fishService.getTransactions(memberId, 0, 20).content()).isEmpty();
    }

    @Test
    @DisplayName("잔액의 long 범위를 초과하는 지급은 거절한다")
    void overflow() {
        credit(Long.MAX_VALUE, "max");
        assertError(() -> credit(1, "overflow"), MemberErrorCode.FISH_BALANCE_OVERFLOW);
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(Long.MAX_VALUE);
        assertThat(fishService.getTransactions(memberId, 0, 20).totalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "reward key", "한글키"})
    @DisplayName("누락되거나 허용 문자가 아닌 작업 식별 키를 거절한다")
    void invalidKey(String key) {
        assertError(() -> fishService.credit(memberId, 100, ATTENDANCE_REWARD, null, key),
                FishErrorCode.INVALID_IDEMPOTENCY_KEY);
    }

    @Test
    @DisplayName("키 길이, 참조 ID 및 지급·차감 사유를 검증한다")
    void invalidMetadata() {
        assertError(() -> fishService.credit(memberId, 100, ATTENDANCE_REWARD, null, "x".repeat(101)),
                FishErrorCode.INVALID_IDEMPOTENCY_KEY);
        assertError(() -> fishService.credit(memberId, 100, ATTENDANCE_REWARD, 0L, key("invalid")),
                FishErrorCode.INVALID_REFERENCE_ID);
        assertError(() -> fishService.credit(memberId, 100, ITEM_PURCHASE, null, key("invalid")),
                FishErrorCode.INVALID_TRANSACTION_TYPE);
        assertError(() -> fishService.debit(memberId, 100, ATTENDANCE_REWARD, null, key("invalid")),
                FishErrorCode.INVALID_TRANSACTION_TYPE);
        assertError(() -> fishService.credit(memberId, 100, null, null, key("invalid")),
                FishErrorCode.INVALID_TRANSACTION_TYPE);
    }

    @Test
    @DisplayName("호출한 업무 트랜잭션이 실패하면 잔액과 이력도 롤백된다")
    void participatesInOuterTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            credit(100, "rollback");
            throw new IllegalStateException("호출한 기능의 후속 작업 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(fishService.getBalance(memberId).balance()).isZero();
        assertThat(fishService.getTransactions(memberId, 0, 20).content()).isEmpty();
        assertThat(credit(100, "rollback").balanceAfter()).isEqualTo(100);
    }

    @Test
    @Timeout(30)
    @DisplayName("동일 지급 요청 6개가 동시에 실행돼도 잔액과 이력은 한 번만 증가한다")
    void concurrentDuplicateCredit() throws Exception {
        List<Object> outcomes = concurrently(6, index -> credit(100, "concurrent"));
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(FishTransactionResponse.class));
        assertThat(outcomes.stream().distinct().count()).isEqualTo(1);
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(100);
        assertThat(fishService.getTransactions(memberId, 0, 20).totalElements()).isEqualTo(1);
    }

    @Test
    @Timeout(30)
    @DisplayName("100개 잔액에서 80개씩 동시 차감하면 한 건만 성공한다")
    void concurrentDebit() throws Exception {
        credit(100, "seed");
        List<Object> outcomes = concurrently(2, index ->
                fishService.debit(memberId, 80, ITEM_PURCHASE, 1L, key("purchase" + index)));
        assertThat(outcomes.stream().filter(FishTransactionResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes).contains(MemberErrorCode.INSUFFICIENT_FISH);
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(20);
        assertThat(fishService.getTransactions(memberId, 0, 20).totalElements()).isEqualTo(2);
    }

    @Test
    @Timeout(30)
    @DisplayName("서로 다른 지급 요청이 동시에 실행돼도 변경이 유실되지 않는다")
    void concurrentDistinctCredits() throws Exception {
        List<Object> outcomes = concurrently(6, index -> credit(100, "reward" + index));
        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome).isInstanceOf(FishTransactionResponse.class));
        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(600);
        assertThat(fishService.getTransactions(memberId, 0, 20).totalElements()).isEqualTo(6);
    }

    @Test
    @DisplayName("내역은 본인 거래만 최신순으로 페이지 조회하며 같은 시각은 ID로 정렬한다")
    void historyPaginationAndOwnership() {
        FishTransactionResponse first = credit(10, "first");
        FishTransactionResponse second = credit(20, "second");
        FishTransactionResponse third = credit(30, "third");
        Long otherId = newMember().getMemberId();
        fishService.credit(otherId, 999, ATTENDANCE_REWARD, null, key("other"));

        var page = fishService.getTransactions(memberId, 0, 2);
        assertThat(page.content()).containsExactly(third, second);
        assertThat(page.totalElements()).isEqualTo(3);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.hasNext()).isTrue();
        assertThat(fishService.getTransactions(memberId, 1, 2).content()).containsExactly(first);
        assertThat(fishService.getTransactions(memberId, 2, 2).content()).isEmpty();
    }

    @Test
    @DisplayName("회원이 없거나 탈퇴 상태이면 조회 및 변경을 거절한다")
    void missingAndWithdrawnMember() {
        assertError(() -> fishService.getBalance(null), GeneralErrorCode.UNAUTHORIZED);
        assertError(() -> fishService.getBalance(Long.MAX_VALUE), GeneralErrorCode.NOT_FOUND);
        assertError(() -> fishService.credit(Long.MAX_VALUE, 1, ATTENDANCE_REWARD, null, key("missing")),
                GeneralErrorCode.NOT_FOUND);
        jdbcTemplate.update("update member set status = 'WITHDRAWN' where member_id = ?", memberId);
        assertError(() -> fishService.getBalance(memberId), FishErrorCode.INACTIVE_MEMBER);
        assertError(() -> fishService.getTransactions(memberId, 0, 20), FishErrorCode.INACTIVE_MEMBER);
        assertError(() -> credit(100, "withdrawn"), FishErrorCode.INACTIVE_MEMBER);
    }

    @Test
    @DisplayName("실제 데일리 퀴즈 생성이 공통 차감 서비스를 거쳐 거래 한 건을 기록한다")
    void dailyQuizUsesSharedDebit() {
        credit(100, "seed");
        quizQuestionRepository.saveAll(IntStream.range(0, 5)
                .mapToObj(index -> QuizQuestion.create(QuizCategory.MACRO_ECONOMY, "MULTIPLE_CHOICE",
                        "문제 " + index, "해설", true)).toList());

        var session = dailyQuizService.createSession(memberId,
                new DailyQuizSessionCreateRequest(QuizCategory.MACRO_ECONOMY));

        assertThat(fishService.getBalance(memberId).balance()).isEqualTo(50);
        var costs = fishService.getTransactions(memberId, 0, 20).content().stream()
                .filter(item -> item.transactionType() == DAILY_QUIZ_COST).toList();
        assertThat(costs).hasSize(1);
        assertThat(costs.get(0).amount()).isEqualTo(-50);
        assertThat(costs.get(0).referenceId()).isEqualTo(session.sessionId());
    }

    @Test
    @DisplayName("API는 인증 회원의 데이터만 공통 응답 형식으로 반환한다")
    void authenticatedApi() throws Exception {
        credit(100, "api");
        Long otherId = newMember().getMemberId();
        mockMvc.perform(get("/api/v1/fish/me/balance").with(asMember(memberId))
                        .param("memberId", otherId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.balance").value(100));
        mockMvc.perform(get("/api/v1/fish/me/transactions").with(asMember(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.content[0].amount").value(100))
                .andExpect(jsonPath("$.result.content[0].idempotencyKey").doesNotExist());
        mockMvc.perform(get("/api/v1/fish/me/transactions").with(asMember(otherId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(0));
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    @DisplayName("페이지 범위 오류를 400으로 반환한다")
    void invalidPageApi(int page, int size) throws Exception {
        mockMvc.perform(get("/api/v1/fish/me/transactions").with(asMember(memberId))
                        .param("page", String.valueOf(page)).param("size", String.valueOf(size)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FISH_400_3"));
    }

    @Test
    @DisplayName("숫자가 아닌 페이지 입력은 서버 오류 대신 400으로 반환한다")
    void malformedPageApi() throws Exception {
        mockMvc.perform(get("/api/v1/fish/me/transactions").with(asMember(memberId)).param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/members/me",
            "/api/v1/fish/me/balance",
            "/api/v1/fish/me/transactions"
    })
    @DisplayName("인증되지 않은 사용자의 API 요청은 카카오 리다이렉트 대신 401을 반환한다")
    void anonymousCannotRead(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_401"));
    }

    @Test
    @DisplayName("Swagger에 생선 조회 경로와 응답 스키마가 등록된다")
    void openApi() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/fish/me/balance'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/fish/me/transactions'].get").exists())
                .andExpect(jsonPath("$.components.schemas.FishBalanceResponse").exists());
    }

    private Member newMember() {
        long sequence = MEMBER_SEQUENCE.incrementAndGet();
        return memberRepository.saveAndFlush(Member.create(sequence, "fish" + sequence));
    }

    private String key(String suffix) {
        return "fish:" + memberId + ":" + suffix;
    }

    private FishTransactionResponse credit(long amount, String suffix) {
        return fishService.credit(memberId, amount, ATTENDANCE_REWARD, null, key(suffix));
    }

    private void assertError(Runnable operation, BaseErrorCode expected) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ProjectException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }

    private RequestPostProcessor asMember(Long id) {
        return authentication(new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }

    private List<Object> concurrently(int count, IntFunction<FishTransactionResponse> operation) throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("동시 실행 시작 시간 초과");
                    }
                    try {
                        return operation.apply(index);
                    } catch (ProjectException exception) {
                        return exception.getErrorCode();
                    }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get(15, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-27T15:00:00Z"), ZoneOffset.UTC);
        }
    }
}
