package demoday.backend.stock.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.domain.StockChange;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.domain.StockDailySnapshot;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(StockServiceIntegrationTest.FixedClockConfig.class)
class StockServiceIntegrationTest {

    private static final AtomicLong MEMBER_SEQUENCE = new AtomicLong();

    @Autowired private StockService stockService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;
    @Autowired private StockChangeRepository stockChangeRepository;
    @Autowired private StockDailySnapshotRepository stockDailySnapshotRepository;

    private Long memberId;

    @BeforeEach
    void createMember() {
        memberId = newMember().getMemberId();
    }

    @Test
    @DisplayName("신규 회원은 초기 주가 100.00을 공통 응답 형식으로 조회한다")
    void initialStock() throws Exception {
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("100.00");
        mockMvc.perform(get("/api/v1/stocks/me").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.currentStock").value(100.00));
    }

    @Test
    @DisplayName("주가가 변경되면 저장된 소수점 값을 그대로 조회한다")
    void changedStock() throws Exception {
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.increaseStock(3);
        member.increaseStock(7);
        memberRepository.saveAndFlush(member);

        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("110.21");
        mockMvc.perform(get("/api/v1/stocks/me").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentStock").value(110.21));
    }

    @Test
    @DisplayName("다른 회원 ID를 요청에 넣어도 인증된 본인의 주가만 조회한다")
    void usesAuthenticatedMember() throws Exception {
        Member other = newMember();
        other.increaseStock(10);
        memberRepository.saveAndFlush(other);

        mockMvc.perform(get("/api/v1/stocks/me")
                        .param("memberId", other.getMemberId().toString())
                        .with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentStock").value(100.00));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/stocks/me", "/api/v1/stocks/me/changes", "/api/v1/stocks/me/history"})
    @DisplayName("탈퇴 회원의 조회는 거절한다")
    void withdrawnMember(String path) throws Exception {
        jdbcTemplate.update("update member set status = 'WITHDRAWN' where member_id = ?", memberId);

        mockMvc.perform(get(path).with(memberAuthentication(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STOCK_403_1"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/stocks/me", "/api/v1/stocks/me/changes", "/api/v1/stocks/me/history"})
    @DisplayName("인증 정보의 회원이 DB에 없으면 404를 반환한다")
    void missingMember(String path) throws Exception {
        mockMvc.perform(get(path).with(memberAuthentication(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/stocks/me", "/api/v1/stocks/me/changes", "/api/v1/stocks/me/history"})
    @DisplayName("비로그인 요청은 기존 카카오 로그인 경로로 이동한다")
    void anonymousRequest(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/api/v1/auth/oauth2/authorization/kakao"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/stocks/me", "/api/v1/stocks/me/changes", "/api/v1/stocks/me/history"})
    @DisplayName("MEMBER 권한이 없는 인증 사용자는 조회할 수 없다")
    void requiresMemberRole(String path) throws Exception {
        mockMvc.perform(get(path).with(authentication(
                        new UsernamePasswordAuthenticationToken(memberId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("서비스에 회원 ID가 없으면 인증 오류로 처리한다")
    void missingPrincipal() {
        assertThatThrownBy(() -> stockService.getCurrentStock(null))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Swagger에 주가 조회 API와 응답 모델이 등록된다")
    void apiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/me/changes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/me/history'].get").exists())
                .andExpect(jsonPath("$.components.schemas.StockHistoryResponse.properties.points").exists())
                .andExpect(jsonPath("$.components.schemas.StockChangeResponse.properties.stockBefore").exists())
                .andExpect(jsonPath("$.components.schemas.StockResponse.properties.currentStock").exists());
    }

    @Test
    @DisplayName("변동 내역이 없으면 기본 페이지 정보와 빈 목록을 반환한다")
    void emptyChanges() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me/changes").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content").isEmpty())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(0))
                .andExpect(jsonPath("$.result.totalPages").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    @DisplayName("본인의 내역만 시각과 ID 내림차순으로 페이징한다")
    void changesAreScopedAndOrdered() throws Exception {
        Member member = memberRepository.findById(memberId).orElseThrow();
        LocalDateTime time = LocalDateTime.of(2026, 9, 29, 12, 0);
        StockChange olderId = saveChange(member, time, StockChangeType.QUIZ_CORRECT);
        StockChange newerId = saveChange(member, time, StockChangeType.STREAK_PENALTY);
        StockChange oldestTime = saveChange(member, time.minusDays(1), StockChangeType.STREAK_RECOVERY);
        Member other = newMember();
        saveChange(other, time.plusDays(1), StockChangeType.ADMIN_ADJUSTMENT);

        mockMvc.perform(get("/api/v1/stocks/me/changes")
                        .param("memberId", other.getMemberId().toString())
                        .param("size", "2").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content.length()").value(2))
                .andExpect(jsonPath("$.result.content[0].stockChangeId").value(newerId.getStockChangeId()))
                .andExpect(jsonPath("$.result.content[1].stockChangeId").value(olderId.getStockChangeId()))
                .andExpect(jsonPath("$.result.content[0].changeType").value("STREAK_PENALTY"))
                .andExpect(jsonPath("$.result.content[0].stockBefore").value(110.25))
                .andExpect(jsonPath("$.result.content[0].stockAfter").value(88.20))
                .andExpect(jsonPath("$.result.content[0].referenceId").value(7))
                .andExpect(jsonPath("$.result.content[0].createdAt").value("2026-09-29T12:00:00"))
                .andExpect(jsonPath("$.result.content[0].idempotencyKey").doesNotExist())
                .andExpect(jsonPath("$.result.totalElements").value(3))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true));

        var lastPage = stockService.getChanges(memberId, 1, 2);
        assertThat(lastPage.content()).extracting(change -> change.stockChangeId())
                .containsExactly(oldestTime.getStockChangeId());
        assertThat(lastPage.page()).isEqualTo(1);
        assertThat(lastPage.hasNext()).isFalse();
        var beyondLastPage = stockService.getChanges(memberId, 2, 2);
        assertThat(beyondLastPage.content()).isEmpty();
        assertThat(beyondLastPage.totalElements()).isEqualTo(3);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101"})
    @DisplayName("잘못된 페이지 범위는 400으로 거절한다")
    void invalidChangePage(int page, int size) throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me/changes")
                        .param("page", String.valueOf(page)).param("size", String.valueOf(size))
                        .with(memberAuthentication(memberId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STOCK_400_1"));
    }

    @Test
    @DisplayName("최대 페이지 크기 100을 허용한다")
    void maximumChangePageSize() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me/changes").param("size", "100")
                        .with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.size").value(100));
    }

    private StockChange saveChange(Member member, LocalDateTime time, StockChangeType type) {
        return stockChangeRepository.saveAndFlush(StockChange.create(
                member, type, new BigDecimal("110.25"), new BigDecimal("88.20"),
                7L, "stock-test:" + UUID.randomUUID(), time
        ));
    }

    @Test
    @DisplayName("기간 생략 시 UTC와 날짜가 다른 KST 오늘을 포함한 최근 30일을 조회한다")
    void defaultHistoryPeriod() throws Exception {
        Member member = memberRepository.findById(memberId).orElseThrow();
        saveSnapshot(member, "2026-08-30", "99.00");
        saveSnapshot(member, "2026-08-31", "100.00");
        saveSnapshot(member, "2026-09-29", "110.25");
        saveSnapshot(member, "2026-09-30", "120.00");
        mockMvc.perform(get("/api/v1/stocks/me/history").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.from").value("2026-08-31"))
                .andExpect(jsonPath("$.result.to").value("2026-09-29"))
                .andExpect(jsonPath("$.result.points.length()").value(2))
                .andExpect(jsonPath("$.result.points[0].date").value("2026-08-31"))
                .andExpect(jsonPath("$.result.points[1].date").value("2026-09-29"));
    }

    @Test
    @DisplayName("본인의 스냅샷만 양 끝 날짜를 포함해 오름차순 조회하고 누락 날짜를 채우지 않는다")
    void historyPoints() throws Exception {
        Member member = memberRepository.findById(memberId).orElseThrow();
        saveSnapshot(member, "2026-09-29", "110.25");
        saveSnapshot(member, "2026-09-27", "105.50");
        saveSnapshot(member, "2026-09-26", "100.00");
        saveSnapshot(member, "2026-09-30", "115.00");
        Member other = newMember();
        saveSnapshot(other, "2026-09-28", "999.00");
        mockMvc.perform(get("/api/v1/stocks/me/history")
                        .param("from", "2026-09-27").param("to", "2026-09-29")
                        .param("memberId", other.getMemberId().toString())
                        .with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points.length()").value(2))
                .andExpect(jsonPath("$.result.points[0].date").value("2026-09-27"))
                .andExpect(jsonPath("$.result.points[0].stockValue").value(105.50))
                .andExpect(jsonPath("$.result.points[1].date").value("2026-09-29"))
                .andExpect(jsonPath("$.result.points[1].stockValue").value(110.25));
    }

    @Test
    @DisplayName("신규 회원은 실시간 주가를 그래프에 추가하지 않고 빈 목록을 반환한다")
    void emptyHistory() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me/history").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.points").isEmpty());
    }

    @Test
    @DisplayName("시작일과 종료일이 같으면 해당 날짜의 스냅샷을 조회한다")
    void singleDayHistory() {
        Member member = memberRepository.findById(memberId).orElseThrow();
        saveSnapshot(member, "2026-09-29", "110.25");
        LocalDate date = LocalDate.of(2026, 9, 29);
        assertThat(stockService.getHistory(memberId, date, date).points()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({",2026-09-29", "2026-09-01,", "2026-09-29,2026-09-01", "2024-01-01,2025-01-01"})
    @DisplayName("기간 일부 누락, 역전, 366일 초과는 거절한다")
    void invalidHistoryPeriod(String from, String to) throws Exception {
        var request = get("/api/v1/stocks/me/history").with(memberAuthentication(memberId));
        if (from != null) request.param("from", from);
        if (to != null) request.param("to", to);
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STOCK_400_2"));
    }

    @Test
    @DisplayName("윤년의 366일 조회를 허용한다")
    void maximumHistoryPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me/history")
                        .param("from", "2024-01-01").param("to", "2024-12-31")
                        .with(memberAuthentication(memberId)))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-date", "2026-02-30"})
    @DisplayName("날짜 형식 오류와 존재하지 않는 날짜를 거절한다")
    void invalidHistoryDate(String date) throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me/history")
                        .param("from", date).param("to", "2026-09-29")
                        .with(memberAuthentication(memberId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    private void saveSnapshot(Member member, String date, String value) {
        stockDailySnapshotRepository.saveAndFlush(StockDailySnapshot.create(
                member, LocalDate.parse(date), new BigDecimal(value)
        ));
    }

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-09-28T15:00:00Z"), ZoneOffset.UTC);
        }
    }

    private Member newMember() {
        long sequence = MEMBER_SEQUENCE.incrementAndGet();
        return memberRepository.saveAndFlush(Member.create(sequence, "stock" + sequence));
    }

    private RequestPostProcessor memberAuthentication(Long id) {
        return authentication(new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
