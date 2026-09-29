package demoday.backend.stock.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockErrorCode;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.dto.StockChangeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-write;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
class StockChangeIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private StockService stockService;
    @Autowired private MemberRepository members;
    @Autowired private StockChangeRepository changes;
    @Autowired private TransactionRetryExecutor retryExecutor;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    private Long memberId;

    @BeforeEach
    void setUp() {
        long id = SEQUENCE.incrementAndGet();
        memberId = members.saveAndFlush(Member.create(id, "write" + id)).getMemberId();
    }

    @Test
    void increasePenaltyRecoveryAndReplay() {
        var first = change("100", "110.25", StockChangeType.QUIZ_CORRECT, "quiz");
        change("110.25", "88.20", StockChangeType.STREAK_PENALTY, "penalty");
        change("88.20", "110.25", StockChangeType.STREAK_RECOVERY, "recovery");
        change("110.25", "120", StockChangeType.ADMIN_ADJUSTMENT, "admin");
        assertThat(change("100.00", "110.250", StockChangeType.QUIZ_CORRECT, "quiz")).isEqualTo(first);
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("120");
        assertThat(stockService.getChanges(memberId, 0, 20).totalElements()).isEqualTo(4);
    }

    @Test
    void changedCommandConflicts() {
        change("100", "110", StockChangeType.QUIZ_CORRECT, "key");
        assertConflict(() -> change("99", "110", StockChangeType.QUIZ_CORRECT, "key"));
        assertConflict(() -> change("100", "111", StockChangeType.QUIZ_CORRECT, "key"));
        assertConflict(() -> change("100", "110", StockChangeType.ADMIN_ADJUSTMENT, "key"));
        assertConflict(() -> stockService.changeStock(memberId, new BigDecimal("100"), new BigDecimal("110"),
                StockChangeType.QUIZ_CORRECT, 2L, key("key")));
        long id = SEQUENCE.incrementAndGet();
        Long other = members.saveAndFlush(Member.create(id, "write" + id)).getMemberId();
        assertConflict(() -> stockService.changeStock(other, new BigDecimal("100"), new BigDecimal("110"),
                StockChangeType.QUIZ_CORRECT, 1L, key("key")));
        assertThat(stockService.getCurrentStock(other).currentStock()).isEqualByComparingTo("100");
    }

    @Test
    void staleStockDoesNotOverwriteNewValue() {
        change("100", "110", StockChangeType.QUIZ_CORRECT, "first");
        assertThatThrownBy(() -> change("100", "120", StockChangeType.QUIZ_CORRECT, "stale"))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.STALE_STOCK));
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("110");
        assertThat(stockService.getChanges(memberId, 0, 20).totalElements()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100.001", "10000000000000000000000000000"})
    void invalidStockValues(String value) {
        assertThatThrownBy(() -> change("100", value, StockChangeType.ADMIN_ADJUSTMENT, "invalid"))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_STOCK_VALUE));
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("100");
        assertThat(stockService.getChanges(memberId, 0, 20).content()).isEmpty();
    }

    @Test
    void zeroAndMaximumAndNoOpAreRepresentable() {
        change("100", "0", StockChangeType.ADMIN_ADJUSTMENT, "zero");
        change("0", "0", StockChangeType.STREAK_PENALTY, "rounded-zero");
        change("0", "9999999999999999999999999999.99", StockChangeType.ADMIN_ADJUSTMENT, "max");
        assertThat(stockService.getCurrentStock(memberId).currentStock())
                .isEqualByComparingTo("9999999999999999999999999999.99");
    }

    @Test
    void missingValuesInvalidReasonAndReferenceAreRejected() {
        assertThatThrownBy(() -> stockService.changeStock(memberId, null, BigDecimal.TEN,
                StockChangeType.ADMIN_ADJUSTMENT, null, key("null-before")))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_STOCK_VALUE));
        assertThatThrownBy(() -> stockService.changeStock(memberId, BigDecimal.valueOf(100), null,
                StockChangeType.ADMIN_ADJUSTMENT, null, key("null-after")))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_STOCK_VALUE));
        assertThatThrownBy(() -> change("100", "110", null, "null-type"))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.INVALID_CHANGE));
        assertThatThrownBy(() -> stockService.changeStock(memberId, BigDecimal.valueOf(100), BigDecimal.TEN,
                StockChangeType.ADMIN_ADJUSTMENT, 0L, key("bad-reference")))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.INVALID_CHANGE));
        assertThat(stockService.getChanges(memberId, 0, 20).content()).isEmpty();
    }

    @Test
    void keyLengthBoundary() {
        String prefix = key("limit");
        String longest = prefix + "a".repeat(100 - prefix.length());
        stockService.changeStock(memberId, BigDecimal.valueOf(100), BigDecimal.TEN,
                StockChangeType.ADMIN_ADJUSTMENT, null, longest);
        assertThatThrownBy(() -> stockService.changeStock(memberId, BigDecimal.TEN, BigDecimal.ONE,
                StockChangeType.ADMIN_ADJUSTMENT, null, longest + "a"))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.INVALID_IDEMPOTENCY_KEY));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"a b", "한글", "a/b"})
    void invalidKeys(String value) {
        assertThatThrownBy(() -> stockService.changeStock(memberId, BigDecimal.valueOf(100), BigDecimal.valueOf(110),
                StockChangeType.QUIZ_CORRECT, 1L, value))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.INVALID_IDEMPOTENCY_KEY));
    }

    @Test
    void callerFailureRollsBackMemberAndHistoryTogether() {
        assertThatThrownBy(() -> retryExecutor.execute(() -> {
            change("100", "110", StockChangeType.QUIZ_CORRECT, "rollback");
            throw new IllegalStateException("후속 업무 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("100");
        assertThat(stockService.getChanges(memberId, 0, 20).content()).isEmpty();
    }

    @Test
    void readOnlyCallerIsRejected() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status ->
                change("100", "110", StockChangeType.QUIZ_CORRECT, "read-only")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(stockService.getChanges(memberId, 0, 20).content()).isEmpty();
    }

    @Test
    void withdrawnMemberIsRejected() {
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", memberId);
        assertThatThrownBy(() -> change("100", "110", StockChangeType.QUIZ_CORRECT, "withdrawn"))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.INACTIVE_MEMBER));
    }

    @Test
    @Timeout(30)
    void concurrentDuplicateAppliesOnce() throws Exception {
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<StockChangeResponse> operation = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                return retryExecutor.execute(() -> change("100", "110", StockChangeType.QUIZ_CORRECT, "concurrent"));
            };
            var first = pool.submit(operation);
            var second = pool.submit(operation);
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
            assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("110");
            assertThat(stockService.getChanges(memberId, 0, 20).totalElements()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private StockChangeResponse change(String before, String after, StockChangeType type, String key) {
        return stockService.changeStock(memberId, new BigDecimal(before), new BigDecimal(after), type, 1L, key(key));
    }

    private String key(String value) { return "stock:" + memberId + ":" + value; }

    private void assertConflict(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.IDEMPOTENCY_CONFLICT));
    }
}
