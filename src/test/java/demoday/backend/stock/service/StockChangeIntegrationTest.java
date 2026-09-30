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
import org.junit.jupiter.params.provider.CsvSource;
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
        var first = change("100", "110", StockChangeType.QUIZ_CORRECT, "quiz");
        var penalty = change("110", "88", StockChangeType.STREAK_PENALTY, "penalty");
        stockService.changeStock(memberId, new BigDecimal("88"), new BigDecimal("110"),
                StockChangeType.STREAK_RECOVERY, penalty.stockChangeId(), key("recovery"));
        change("110", "120", StockChangeType.ADMIN_ADJUSTMENT, "admin");
        assertThat(change("100.00", "110.000", StockChangeType.QUIZ_CORRECT, "quiz")).isEqualTo(first);
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("120");
        assertThat(changeCount()).isEqualTo(4);
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
        assertThat(changeCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "100.001", "10000000000000000000000000000"})
    void invalidStockValues(String value) {
        assertThatThrownBy(() -> change("100", value, StockChangeType.ADMIN_ADJUSTMENT, "invalid"))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(MemberErrorCode.INVALID_STOCK_VALUE));
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("100");
        assertThat(changeCount()).isZero();
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
        assertThat(changeCount()).isZero();
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
        assertThat(changeCount()).isZero();
    }

    @Test
    void readOnlyCallerIsRejected() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);
        assertThatThrownBy(() -> readOnly.execute(status ->
                change("100", "110", StockChangeType.QUIZ_CORRECT, "read-only")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(changeCount()).isZero();
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
            assertThat(changeCount()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
            pool.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "QUIZ_CORRECT,99", "QUIZ_CORRECT,100", "QUIZ_CORRECT,100.50",
            "QUIZ_CORRECT,110.25", "QUIZ_CORRECT,111",
            "STREAK_PENALTY,101", "STREAK_PENALTY,100", "STREAK_PENALTY,90", "STREAK_PENALTY,79.99"
    })
    void rejectsValuesInconsistentWithReason(StockChangeType type, String after) {
        assertRuleError(() -> change("100", after, type, "invalid-rule"));
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("100");
        assertThat(changeCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void acceptsEveryIntegerQuizPercentWithRounding(int percent) {
        change("100", "100.05", StockChangeType.ADMIN_ADJUSTMENT, "setup");
        BigDecimal expected = new BigDecimal("100.05")
                .multiply(BigDecimal.ONE.add(BigDecimal.valueOf(percent).movePointLeft(2)))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        var result = change("100.05", expected.toPlainString(), StockChangeType.QUIZ_CORRECT, "quiz-rule");
        assertThat(result.stockAfter()).isEqualByComparingTo(expected);
    }

    @Test
    void roundedSmallValuesMayRemainUnchanged() {
        change("100", "0.01", StockChangeType.ADMIN_ADJUSTMENT, "setup");
        change("0.01", "0.01", StockChangeType.QUIZ_CORRECT, "quiz-rule");
        change("0.01", "0.01", StockChangeType.STREAK_PENALTY, "penalty-rule");
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("0.01");
    }

    @Test
    void penaltyUsesHalfUpToTwoDecimalPlaces() {
        change("100", "100.02", StockChangeType.ADMIN_ADJUSTMENT, "setup");
        change("100.02", "80.02", StockChangeType.STREAK_PENALTY, "rounded-penalty");
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("80.02");
    }

    @Test
    void recoveryRequiresOwnPenaltyAndExactOriginalValue() {
        var penalty = change("100", "80", StockChangeType.STREAK_PENALTY, "penalty-rule");
        assertRuleError(() -> recover("80", "99.99", penalty.stockChangeId(), "wrong-target"));
        assertRuleError(() -> recover("80", "120", penalty.stockChangeId(), "too-high"));
        assertRuleError(() -> recover("80", "100", null, "no-reference"));
        assertRuleError(() -> recover("80", "100", Long.MAX_VALUE, "missing-reference"));
        var adjustment = change("80", "81", StockChangeType.ADMIN_ADJUSTMENT, "adjustment");
        assertRuleError(() -> recover("81", "80", adjustment.stockChangeId(), "wrong-type"));
        long sequence = SEQUENCE.incrementAndGet();
        Long otherId = members.saveAndFlush(Member.create(sequence, "write" + sequence)).getMemberId();
        var otherPenalty = stockService.changeStock(otherId, BigDecimal.valueOf(100), BigDecimal.valueOf(80),
                StockChangeType.STREAK_PENALTY, null, "other-penalty:" + otherId);
        assertRuleError(() -> recover("81", "100", otherPenalty.stockChangeId(), "wrong-member"));
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("81");
        assertThat(changeCount()).isEqualTo(2);
        var original = recover("81", "100", penalty.stockChangeId(), "correct-recovery");
        change("100", "110", StockChangeType.QUIZ_CORRECT, "later");
        assertThat(recover("81", "100", penalty.stockChangeId(), "correct-recovery")).isEqualTo(original);
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("110");
    }

    private StockChangeResponse recover(String before, String after, Long penaltyId, String key) {
        return stockService.changeStock(memberId, new BigDecimal(before), new BigDecimal(after),
                StockChangeType.STREAK_RECOVERY, penaltyId, key(key));
    }

    private void assertRuleError(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.INVALID_CHANGE_VALUE));
    }

    private StockChangeResponse change(String before, String after, StockChangeType type, String key) {
        return stockService.changeStock(memberId, new BigDecimal(before), new BigDecimal(after), type, 1L, key(key));
    }

    private long changeCount() {
        return jdbc.queryForObject("select count(*) from stock_change where member_id=?", Long.class, memberId);
    }

    private String key(String value) { return "stock:" + memberId + ":" + value; }

    private void assertConflict(Runnable operation) {
        assertThatThrownBy(operation::run).isInstanceOfSatisfying(ProjectException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(StockErrorCode.IDEMPOTENCY_CONFLICT));
    }
}
