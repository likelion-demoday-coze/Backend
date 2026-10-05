package demoday.backend.stock.service;

import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.domain.StockChange;
import demoday.backend.stock.domain.StockDailySnapshot;
import demoday.backend.stock.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-snapshot;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@Import(StockSnapshotIntegrationTest.TimeConfig.class)
class StockSnapshotIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final LocalDate DATE = LocalDate.of(2026, 10, 4);
    @Autowired private StockSnapshotService service;
    @Autowired private MemberRepository members;
    @Autowired private StockChangeRepository changes;
    @Autowired private StockDailySnapshotRepository snapshots;
    @Autowired private JdbcTemplate jdbc;
    private final List<Long> createdIds = new ArrayList<>();

    @AfterEach void clean() {
        for (Long id : createdIds) {
            jdbc.update("delete from stock_daily_snapshot where member_id=?", id);
            jdbc.update("delete from stock_change where member_id=?", id);
            jdbc.update("delete from member where member_id=?", id);
        }
    }
    @Test void delayedCaptureUsesFirstPostMidnightBeforeAndOrdersEqualTimesById() {
        Member member = member("2026-10-04T12:00:00", "96.80");
        change(member, "2026-10-04T23:59:59", "100", "110");
        change(member, "2026-10-05T00:00:00", "110", "88");
        change(member, "2026-10-05T00:00:00", "88", "96.80");
        assertThat(service.capture(DATE)).isEqualTo(1);
        assertThat(value(member)).isEqualByComparingTo("110");
        assertThat(members.findById(member.getMemberId()).orElseThrow().getCurrentStock()).isEqualByComparingTo("96.80");
    }
    @Test void unchangedMembersAreIncludedButMidnightSignupAndWithdrawnMembersAreExcluded() {
        Member active = member("2026-10-04T23:59:59", "123");
        member("2026-10-05T00:00:00", "100");
        Member withdrawn = member("2026-10-03T12:00:00", "200");
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", withdrawn.getMemberId());
        assertThat(service.capture(DATE)).isEqualTo(1);
        assertThat(value(active)).isEqualByComparingTo("123");
    }
    @Test void partiallySavedDateFillsMissingMembersWithoutOverwritingExistingValue() {
        Member first = member("2026-10-04T12:00:00", "999");
        Member second = member("2026-10-04T12:00:00", "200");
        snapshots.saveAndFlush(StockDailySnapshot.create(first, DATE, new BigDecimal("110")));
        assertThat(service.capture(DATE)).isEqualTo(1);
        assertThat(value(first)).isEqualByComparingTo("110");
        assertThat(value(second)).isEqualByComparingTo("200");
        assertThat(service.capture(DATE)).isZero();
    }
    @Test void concurrentSchedulerAndRankingCaptureCreateOnlyOneRowPerMember() throws Exception {
        Member first = member("2026-10-04T12:00:00", "100");
        Member second = member("2026-10-04T12:00:00", "200");
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var one = pool.submit(() -> { start.await(); return service.capture(DATE); });
            var two = pool.submit(() -> { start.await(); return service.capture(DATE); });
            start.countDown();
            assertThat(one.get(20, TimeUnit.SECONDS) + two.get(20, TimeUnit.SECONDS)).isEqualTo(2);
        } finally { pool.shutdownNow(); }
        assertThat(snapshots.findAllBySnapshotDate(DATE)).extracting(s -> s.getMember().getMemberId())
                .containsExactlyInAnyOrder(first.getMemberId(), second.getMemberId());
    }
    @Test void rejectsTodayFutureAndNullDateWithoutWriting() {
        for (LocalDate date : new LocalDate[]{null, DATE.plusDays(1), DATE.plusDays(2)})
            assertThatThrownBy(() -> service.capture(date)).isInstanceOf(IllegalArgumentException.class);
        assertThat(snapshots.count()).isZero();
    }
    private Member member(String joined, String stock) {
        long number = SEQUENCE.incrementAndGet();
        var member = members.saveAndFlush(Member.create(number, "ss" + number));
        createdIds.add(member.getMemberId());
        jdbc.update("update member set created_at=?, current_stock=? where member_id=?", LocalDateTime.parse(joined), new BigDecimal(stock), member.getMemberId());
        return member;
    }
    private void change(Member member, String time, String before, String after) {
        changes.saveAndFlush(StockChange.create(member, StockChangeType.ADMIN_ADJUSTMENT, new BigDecimal(before),
                new BigDecimal(after), null, UUID.randomUUID().toString(), LocalDateTime.parse(time)));
    }
    private BigDecimal value(Member member) {
        return snapshots.findAllBySnapshotDate(DATE).stream().filter(s -> s.getMember().getMemberId().equals(member.getMemberId()))
                .findFirst().orElseThrow().getStockValue();
    }
    @TestConfiguration static class TimeConfig {
        @Bean @Primary Clock snapshotClock() { return Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneOffset.UTC); }
    }
}
