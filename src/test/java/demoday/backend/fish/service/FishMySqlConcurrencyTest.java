package demoday.backend.fish.service;

import demoday.backend.fish.code.FishErrorCode;
import demoday.backend.fish.dto.FishTransactionResponse;
import demoday.backend.fish.repository.FishTransactionRepository;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static demoday.backend.fish.code.FishTransactionType.ATTENDANCE_REWARD;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Docker가 있는 환경에서만 실제 InnoDB의 gap lock을 재현한다. 기존 개발 DB는 사용하지 않는다. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
})
class FishMySqlConcurrencyTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.36");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired private FishService fishService;
    @Autowired private TransactionRetryExecutor retryExecutor;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbc;
    @MockitoSpyBean private FishTransactionRepository transactionRepository;
    private Long firstMember;
    private Long secondMember;

    @BeforeEach
    void setUp() {
        // Testcontainers가 생성한 전용 DB 안에서만 초기화한다.
        transactionRepository.deleteAllInBatch();
        memberRepository.deleteAllInBatch();
        jdbc.execute("create table if not exists retry_probe (id bigint auto_increment primary key, member_id bigint not null)");
        jdbc.update("delete from retry_probe");
        firstMember = memberRepository.saveAndFlush(Member.create(1L, "first")).getMemberId();
        secondMember = memberRepository.saveAndFlush(Member.create(2L, "second")).getMemberId();
        assertThat(jdbc.queryForObject("select @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
    }

    @Test
    @Timeout(45)
    void differentMembersInSameGapRetryWholeTransactionAndBothSucceed() throws Exception {
        forceFirstTwoMissingKeyReadsToOverlap();
        AtomicInteger attempts = new AtomicInteger();
        List<Object> outcomes = runPair("gap:1", "gap:2", attempts);

        assertThat(outcomes).allSatisfy(result -> assertThat(result).isInstanceOf(FishTransactionResponse.class));
        assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
        assertThat(fishService.getBalance(firstMember).balance()).isEqualTo(100);
        assertThat(fishService.getBalance(secondMember).balance()).isEqualTo(100);
        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from retry_probe", Long.class)).isEqualTo(2);
    }

    @Test
    @Timeout(45)
    void differentMembersUsingSameKeyProduceOneSuccessAndOneConflict() throws Exception {
        forceFirstTwoMissingKeyReadsToOverlap();
        List<Object> outcomes = runPair("shared:key", "shared:key", new AtomicInteger());

        assertThat(outcomes.stream().filter(FishTransactionResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes).contains(FishErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(fishService.getBalance(firstMember).balance() + fishService.getBalance(secondMember).balance())
                .isEqualTo(100);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from retry_probe", Long.class)).isEqualTo(1);
    }

    @Test
    void mysqlUniqueViolationIsConvertedToIdempotencyConflict() {
        fishService.credit(firstMember, 100, ATTENDANCE_REWARD, null, "duplicate");
        doReturn(Optional.empty()).when(transactionRepository).findByIdempotencyKey("duplicate");

        assertThatThrownBy(() -> retryExecutor.execute(() ->
                fishService.credit(secondMember, 100, ATTENDANCE_REWARD, null, "duplicate")))
                .isInstanceOfSatisfying(ProjectException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(FishErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(fishService.getBalance(secondMember).balance()).isZero();
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    private void forceFirstTwoMissingKeyReadsToOverlap() {
        CyclicBarrier bothReadGap = new CyclicBarrier(2);
        AtomicInteger reads = new AtomicInteger();
        doAnswer(invocation -> {
            Object result = invocation.callRealMethod();
            if (reads.incrementAndGet() <= 2) {
                assertThat((Optional<?>) result).isEmpty();
                bothReadGap.await(10, TimeUnit.SECONDS);
            }
            return result;
        }).when(transactionRepository).findByIdempotencyKey(anyString());
    }

    private List<Object> runPair(String firstKey, String secondKey, AtomicInteger attempts) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> execute(firstMember, firstKey, attempts));
            var second = pool.submit(() -> execute(secondMember, secondKey, attempts));
            return List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private Object execute(Long memberId, String key, AtomicInteger attempts) {
        try {
            return retryExecutor.execute(() -> {
                attempts.incrementAndGet();
                jdbc.update("insert into retry_probe (member_id) values (?)", memberId);
                return fishService.credit(memberId, 100, ATTENDANCE_REWARD, null, key);
            });
        } catch (ProjectException exception) {
            return exception.getErrorCode();
        }
    }
}
