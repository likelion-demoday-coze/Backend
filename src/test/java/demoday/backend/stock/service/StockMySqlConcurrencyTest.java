package demoday.backend.stock.service;

import demoday.backend.stock.code.StockErrorCode;
import demoday.backend.stock.dto.StockChangeResponse;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.support.StockChangeReadHook;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static demoday.backend.stock.code.StockChangeType.ADMIN_ADJUSTMENT;
import static org.assertj.core.api.Assertions.*;

/** Docker가 있는 환경에서만 실제 InnoDB의 gap lock을 재현한다. 기존 개발 DB는 사용하지 않는다. */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.open-in-view=false",
        // 전용 컨테이너 삭제로 정리하므로 컨테이너 종료 후 DROP 연결을 시도하지 않는다.
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ"
})
class StockMySqlConcurrencyTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.36");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @Autowired private StockService stockService;
    @Autowired private TransactionRetryExecutor retryExecutor;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StockChangeRepository transactionRepository;
    private StockChangeReadHook readHook;
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

    @AfterEach
    void removeReadHook() {
        if (readHook != null) {
            readHook.close();
            readHook = null;
        }
    }

    @Test
    @Timeout(45)
    void differentMembersInSameGapRetryWholeTransactionAndBothSucceed() throws Exception {
        forceFirstTwoMissingKeyReadsToOverlap();
        AtomicInteger attempts = new AtomicInteger();
        List<Object> outcomes = runPair("gap:1", "gap:2", attempts);

        assertThat(outcomes).allSatisfy(result -> assertThat(result).isInstanceOf(StockChangeResponse.class));
        assertThat(attempts.get()).isGreaterThanOrEqualTo(3);
        assertThat(stockService.getCurrentStock(firstMember).currentStock().intValueExact()).isEqualTo(110);
        assertThat(stockService.getCurrentStock(secondMember).currentStock().intValueExact()).isEqualTo(110);
        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from retry_probe", Long.class)).isEqualTo(2);
    }

    @Test
    @Timeout(45)
    void differentMembersUsingSameKeyProduceOneSuccessAndOneConflict() throws Exception {
        forceFirstTwoMissingKeyReadsToOverlap();
        List<Object> outcomes = runPair("shared:key", "shared:key", new AtomicInteger());

        assertThat(outcomes.stream().filter(StockChangeResponse.class::isInstance).count()).isEqualTo(1);
        assertThat(outcomes).contains(StockErrorCode.IDEMPOTENCY_CONFLICT);
        assertThat(stockService.getCurrentStock(firstMember).currentStock().intValueExact() + stockService.getCurrentStock(secondMember).currentStock().intValueExact())
                .isEqualTo(210);
        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from retry_probe", Long.class)).isEqualTo(1);
    }

    @Test
    void mysqlUniqueViolationIsConvertedToIdempotencyConflict() {
        change(firstMember, "duplicate");
        readHook = StockChangeReadHook.install(transactionRepository, result -> Optional.empty());

        assertThatThrownBy(() -> retryExecutor.execute(() ->
                change(secondMember, "duplicate")))
                .isInstanceOfSatisfying(ProjectException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(StockErrorCode.IDEMPOTENCY_CONFLICT));
        assertThat(stockService.getCurrentStock(secondMember).currentStock().intValueExact()).isEqualTo(100);
        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    private void forceFirstTwoMissingKeyReadsToOverlap() {
        CyclicBarrier bothReadGap = new CyclicBarrier(2);
        AtomicInteger reads = new AtomicInteger();
        readHook = StockChangeReadHook.install(transactionRepository, result -> {
            if (reads.incrementAndGet() <= 2) {
                assertThat(result).isEmpty();
                bothReadGap.await(10, TimeUnit.SECONDS);
            }
            return result;
        });
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

    private StockChangeResponse change(Long memberId, String key) {
        return stockService.changeStock(memberId, java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(110), ADMIN_ADJUSTMENT, null, key);
    }

    private Object execute(Long memberId, String key, AtomicInteger attempts) {
        try {
            return retryExecutor.execute(() -> {
                attempts.incrementAndGet();
                jdbc.update("insert into retry_probe (member_id) values (?)", memberId);
                return change(memberId, key);
            });
        } catch (ProjectException exception) {
            return exception.getErrorCode();
        }
    }
}

