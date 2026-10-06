package demoday.backend.stock.service;

import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.code.StockGraphPeriod;
import demoday.backend.stock.domain.StockChange;
import demoday.backend.stock.domain.StockDailySnapshot;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-graph;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
@Import(StockGraphIntegrationTest.TimeConfig.class)
class StockGraphIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private StockService service;
    @Autowired private MemberRepository members;
    @Autowired private StockChangeRepository changes;
    @Autowired private StockDailySnapshotRepository snapshots;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    private Member member;

    @BeforeEach
    void setUp() {
        member = newMember();
        jdbc.update("update member set created_at=? where member_id=?", LocalDateTime.parse("2024-01-01T12:00:00"), member.getMemberId());
    }

    @Test
    void dayIncludesMidnightPenaltyAndOrdersEqualTimesById() {
        change(member, "2026-09-29T23:59:59", "90", "100");
        change(member, "2026-09-30T00:00:00", "100", "80");
        change(member, "2026-09-30T00:10:00", "80", "88");
        change(member, "2026-09-30T00:10:00", "88", "100");
        change(newMember(), "2026-09-30T00:10:00", "100", "999");
        var result = service.getGraph(member.getMemberId(), StockGraphPeriod.DAY);
        assertThat(result.from()).isEqualTo("2026-09-30T00:00:00");
        assertThat(result.points()).extracting(point -> point.stockValue())
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("80.00"), new BigDecimal("88.00"),
                        new BigDecimal("100.00"), new BigDecimal("100.00"));
        assertThat(result.changeRate()).isEqualByComparingTo("0");
    }

    @Test
    void weekIncludesTodayAndPreviousSixDays() {
        change(member, "2026-09-23T23:59:59", "100", "120");
        change(member, "2026-09-24T00:00:00", "120", "125");
        change(member, "2026-09-29T12:00:00", "125", "150");
        current("150");
        var result = service.getGraph(member.getMemberId(), StockGraphPeriod.WEEK);
        assertThat(result.from()).isEqualTo("2026-09-24T00:00:00");
        assertThat(result.startStock()).isEqualByComparingTo("120");
        assertThat(result.changeAmount()).isEqualByComparingTo("30");
        assertThat(result.changeRate()).isEqualByComparingTo("25");
        assertThat(result.points()).hasSize(4);
    }

    @Test
    void allHasNoYearLimitAndUsesSnapshotsPlusCurrent() {
        snapshots.saveAndFlush(StockDailySnapshot.create(member, LocalDate.of(2024, 1, 2), new BigDecimal("105")));
        snapshots.saveAndFlush(StockDailySnapshot.create(member, LocalDate.of(2026, 9, 29), new BigDecimal("120")));
        snapshots.saveAndFlush(StockDailySnapshot.create(newMember(), LocalDate.of(2025, 1, 1), new BigDecimal("999")));
        change(member, "2026-09-30T00:10:00", "120", "130");
        current("130");
        var result = service.getGraph(member.getMemberId(), StockGraphPeriod.ALL);
        assertThat(result.from()).isEqualTo("2024-01-01T12:00:00");
        assertThat(result.estimatedStart()).isFalse();
        assertThat(result.points()).hasSize(4);
        assertThat(result.points().get(1).timestamp()).isEqualTo("2024-01-03T00:00:00");
        assertThat(result.points().get(3).stockValue()).isEqualByComparingTo(result.currentStock());
        assertThat(result.changeRate()).isEqualByComparingTo("30");
    }

    @Test
    void newMemberStartsAtSignupAndHasFlatLine() {
        jdbc.update("update member set created_at=? where member_id=?", LocalDateTime.parse("2026-09-30T00:05:00"), member.getMemberId());
        var result = service.getGraph(member.getMemberId(), StockGraphPeriod.DAY);
        assertThat(result.from()).isEqualTo("2026-09-30T00:05:00");
        assertThat(result.points()).hasSize(2);
        assertThat(result.changeAmount()).isZero();
        assertThat(newMember().getCreatedAt()).isNotNull();
    }

    @Test
    void zeroBaselineHasNoPercentage() {
        change(member, "2026-09-30T00:01:00", "0", "10");
        current("10");
        assertThat(service.getGraph(member.getMemberId(), StockGraphPeriod.DAY).changeRate()).isNull();
    }

    @Test
    void legacyMemberUsesEarliestKnownRecordAndMarksEstimate() {
        jdbc.update("update member set created_at=null where member_id=?", member.getMemberId());
        change(member, "2026-09-20T12:00:00", "110", "120");
        snapshots.saveAndFlush(StockDailySnapshot.create(member, LocalDate.of(2026, 9, 19), new BigDecimal("110")));
        current("120");
        var result = service.getGraph(member.getMemberId(), StockGraphPeriod.ALL);
        assertThat(result.estimatedStart()).isTrue();
        assertThat(result.from()).isEqualTo("2026-09-20T00:00:00");
        assertThat(result.startStock()).isEqualByComparingTo("110");
    }

    @Test
    void defaultHttpPeriodIsAllAndUnknownPeriodIsBadRequest() throws Exception {
        var auth = authentication(new UsernamePasswordAuthenticationToken(member.getMemberId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
        mvc.perform(get("/api/v1/stocks/me/graph").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.period").value("ALL"))
                .andExpect(jsonPath("$.result.currentStock").value(100));
        mvc.perform(get("/api/v1/stocks/me/graph").param("period", "YEAR").with(auth))
                .andExpect(status().isBadRequest());
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", member.getMemberId());
        mvc.perform(get("/api/v1/stocks/me/graph").with(auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/stocks/me/graph"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_401"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/me/graph'].get").exists());
    }

    private Member newMember() {
        long id = SEQUENCE.incrementAndGet();
        return members.saveAndFlush(Member.create(id, "graph" + id));
    }
    private void current(String value) {
        jdbc.update("update member set current_stock=? where member_id=?", new BigDecimal(value), member.getMemberId());
    }
    private void change(Member owner, String time, String before, String after) {
        changes.saveAndFlush(StockChange.create(owner, StockChangeType.ADMIN_ADJUSTMENT,
                new BigDecimal(before), new BigDecimal(after), null, UUID.randomUUID().toString(), LocalDateTime.parse(time)));
    }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock graphClock() {
            return Clock.fixed(Instant.parse("2026-09-29T15:30:00Z"), ZoneOffset.UTC);
        }
    }
}
