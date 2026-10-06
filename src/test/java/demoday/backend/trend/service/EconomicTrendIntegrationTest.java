package demoday.backend.trend.service;

import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.trend.code.*;
import demoday.backend.trend.domain.*;
import demoday.backend.trend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:economictrend;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(EconomicTrendIntegrationTest.TimeConfig.class)
@Transactional
class EconomicTrendIntegrationTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 5, 8, 0);
    @Autowired private EconomicTrendService service;
    @Autowired private MemberRepository members;
    @Autowired private TrendGenerationRepository generations;
    @Autowired private EconomicTrendRepository trends;
    @Autowired private EconomicTermRepository terms;
    @Autowired private TrendReferenceRepository references;
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    private Member member;

    @BeforeEach
    void setUp() { member = members.saveAndFlush(Member.create(81001L, "trend")); }

    @Test
    void todayContentUsesKstDateAndDisplayOrder() throws Exception {
        create(NOW, TrendGenerationStatus.SUCCESS, 3);
        create(NOW.minusDays(1), TrendGenerationStatus.SUCCESS, 3);
        mvc.perform(get("/api/v1/economic-trends/today").with(auth(member.getMemberId(), "ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.date").value("2026-10-05"))
                .andExpect(jsonPath("$.result.contentDate").value("2026-10-05"))
                .andExpect(jsonPath("$.result.status").value("READY"))
                .andExpect(jsonPath("$.result.generatedAt").value("2026-10-05T08:00:00"))
                .andExpect(jsonPath("$.result.items.length()").value(3))
                .andExpect(jsonPath("$.result.items[0].displayOrder").value(1))
                .andExpect(jsonPath("$.result.items[1].displayOrder").value(2))
                .andExpect(jsonPath("$.result.items[2].displayOrder").value(3));
        assertThat(generations.count()).isEqualTo(2);
        assertThat(trends.count()).isEqualTo(6);
    }

    @Test
    void pendingAndFailedGenerationsDoNotReplaceLastSuccess() {
        TrendGeneration valid = create(NOW.minusDays(1), TrendGenerationStatus.SUCCESS, 3);
        create(NOW.minusHours(1), TrendGenerationStatus.FAILED, 3);
        create(NOW, TrendGenerationStatus.PROCESSING, 2);
        var response = service.getToday(member.getMemberId());
        assertThat(response.status()).isEqualTo(TrendContentStatus.FALLBACK);
        assertThat(response.contentDate()).isEqualTo(NOW.minusDays(1).toLocalDate());
        assertThat(response.items()).allSatisfy(item -> assertThat(trends.findById(item.trendId()).orElseThrow()
                .getTrendGeneration().getTrendGenerationId()).isEqualTo(valid.getTrendGenerationId()));
    }

    @Test
    void incompleteSuccessDoesNotExposePartialContent() {
        create(NOW.minusDays(1), TrendGenerationStatus.SUCCESS, 3);
        create(NOW, TrendGenerationStatus.SUCCESS, 2);
        var response = service.getToday(member.getMemberId());
        assertThat(response.status()).isEqualTo(TrendContentStatus.FALLBACK);
        assertThat(response.items()).hasSize(3);
    }

    @Test
    void exact72HourBoundaryIsAllowed() {
        create(NOW.minusHours(72), TrendGenerationStatus.SUCCESS, 3);
        assertThat(service.getToday(member.getMemberId()).status()).isEqualTo(TrendContentStatus.FALLBACK);
    }

    @Test
    void olderThan72HoursAndFutureContentAreNotServed() {
        create(NOW.minusHours(72).minusSeconds(1), TrendGenerationStatus.SUCCESS, 3);
        create(NOW.plusSeconds(1), TrendGenerationStatus.SUCCESS, 3);
        var response = service.getToday(member.getMemberId());
        assertThat(response.status()).isEqualTo(TrendContentStatus.PREPARING);
        assertThat(response.items()).isEmpty();
    }

    @Test
    void noContentReturnsPreparingWithMessage() throws Exception {
        mvc.perform(get("/api/v1/economic-trends/today").with(auth(member.getMemberId(), "ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PREPARING"))
                .andExpect(jsonPath("$.result.message").value("경제 트렌드를 준비하고 있어요"))
                .andExpect(jsonPath("$.result.items").isEmpty());
        assertThat(service.getToday(member.getMemberId()).contentDate()).isNull();
    }

    @Test
    void requestCannotChooseAnotherMemberAndSecurityRejectsInvalidAccounts() throws Exception {
        mvc.perform(get("/api/v1/economic-trends/today")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/economic-trends/today").with(auth(member.getMemberId(), "ROLE_GUEST")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/economic-trends/today").with(auth(Long.MAX_VALUE, "ROLE_MEMBER")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/economic-trends/today").param("memberId", Long.MAX_VALUE + "")
                        .with(auth(member.getMemberId(), "ROLE_MEMBER"))).andExpect(status().isOk());
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", member.getMemberId());
        entityManager.clear();
        mvc.perform(get("/api/v1/economic-trends/today").with(auth(member.getMemberId(), "ROLE_MEMBER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TREND_403_1"));
    }

    @Test
    void documentsRoute() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/economic-trends/today'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/economic-trends/{trendId}'].get").exists());
    }

    @Test
    void detailReturnsOnlySelectedTrendsSummaryTermsAndReferences() throws Exception {
        TrendGeneration generation = create(NOW, TrendGenerationStatus.SUCCESS, 3);
        List<EconomicTrend> items = trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(
                generation.getTrendGenerationId());
        EconomicTrend selected = items.get(0);
        terms.saveAndFlush(EconomicTerm.create(selected, "기준금리", "중앙은행이 정하는 정책 금리"));
        terms.saveAndFlush(EconomicTerm.create(items.get(1), "다른 용어", "다른 트렌드 설명"));
        references.saveAndFlush(TrendReference.create(selected, "금리 발표", "https://example.com/rates",
                "경제 기관", NOW.toLocalDate().minusDays(1)));
        references.saveAndFlush(TrendReference.create(items.get(1), "다른 출처", "https://example.com/other",
                "다른 기관", NOW.toLocalDate()));
        entityManager.clear();
        mvc.perform(get("/api/v1/economic-trends/{trendId}", selected.getEconomicTrendId())
                        .with(auth(member.getMemberId(), "ROLE_MEMBER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.trendId").value(selected.getEconomicTrendId()))
                .andExpect(jsonPath("$.result.contentDate").value("2026-10-05"))
                .andExpect(jsonPath("$.result.generatedAt").value("2026-10-05T08:00:00"))
                .andExpect(jsonPath("$.result.summary").value("상세 요약 1"))
                .andExpect(jsonPath("$.result.terms.length()").value(1))
                .andExpect(jsonPath("$.result.terms[0].name").value("기준금리"))
                .andExpect(jsonPath("$.result.terms[0].description").value("중앙은행이 정하는 정책 금리"))
                .andExpect(jsonPath("$.result.references.length()").value(1))
                .andExpect(jsonPath("$.result.references[0].url").value("https://example.com/rates"))
                .andExpect(jsonPath("$.result.references[0].publisher").value("경제 기관"))
                .andExpect(jsonPath("$.result.references[0].publishedDate").value("2026-10-04"));
        assertThat(terms.count()).isEqualTo(2);
        assertThat(references.count()).isEqualTo(2);
    }

    @Test
    void detailAllowsPreviousContentAndExact72HoursEvenWithNewerContent() {
        TrendGeneration old = create(NOW.minusHours(72), TrendGenerationStatus.SUCCESS, 3);
        Long oldId = trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(
                old.getTrendGenerationId()).get(0).getEconomicTrendId();
        create(NOW, TrendGenerationStatus.SUCCESS, 3);
        var detail = service.getDetail(member.getMemberId(), oldId);
        assertThat(detail.contentDate()).isEqualTo(NOW.minusHours(72).toLocalDate());
        assertThat(detail.terms()).isEmpty();
        assertThat(detail.references()).isEmpty();
    }

    @Test
    void detailHidesUnsuccessfulIncompleteExpiredAndFutureGenerations() throws Exception {
        List<TrendGeneration> unavailable = List.of(
                create(NOW, TrendGenerationStatus.PENDING, 3),
                create(NOW.minusMinutes(1), TrendGenerationStatus.PROCESSING, 3),
                create(NOW.minusMinutes(2), TrendGenerationStatus.FAILED, 3),
                create(NOW.minusMinutes(3), TrendGenerationStatus.SUCCESS, 2),
                create(NOW.minusHours(72).minusSeconds(1), TrendGenerationStatus.SUCCESS, 3),
                create(NOW.plusSeconds(1), TrendGenerationStatus.SUCCESS, 3));
        for (TrendGeneration generation : unavailable) {
            Long id = trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(
                    generation.getTrendGenerationId()).get(0).getEconomicTrendId();
            mvc.perform(get("/api/v1/economic-trends/{trendId}", id).with(auth(member.getMemberId(), "ROLE_MEMBER")))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TREND_404_1"));
        }
    }

    @Test
    void detailValidatesIdAndSecurityBeforeReturningContent() throws Exception {
        TrendGeneration generation = create(NOW, TrendGenerationStatus.SUCCESS, 3);
        Long id = trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(
                generation.getTrendGenerationId()).get(0).getEconomicTrendId();
        mvc.perform(get("/api/v1/economic-trends/{trendId}", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/economic-trends/{trendId}", id).with(auth(member.getMemberId(), "ROLE_GUEST")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/economic-trends/{trendId}", id).with(auth(Long.MAX_VALUE, "ROLE_MEMBER")))
                .andExpect(status().isNotFound());
        for (String invalid : List.of("0", "-1", "invalid")) {
            mvc.perform(get("/api/v1/economic-trends/" + invalid).with(auth(member.getMemberId(), "ROLE_MEMBER")))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/economic-trends/{trendId}", Long.MAX_VALUE).with(auth(member.getMemberId(), "ROLE_MEMBER")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TREND_404_1"));
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", member.getMemberId());
        entityManager.clear();
        mvc.perform(get("/api/v1/economic-trends/{trendId}", id).with(auth(member.getMemberId(), "ROLE_MEMBER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("TREND_403_1"));
    }

    private TrendGeneration create(LocalDateTime date, TrendGenerationStatus status, int count) {
        TrendGeneration generation = generations.saveAndFlush(TrendGeneration.create(date, status, 1, null));
        for (int order = count; order >= 1; order--) {
            trends.saveAndFlush(EconomicTrend.create(generation, order, "경제 이슈 " + order, "상세 요약 " + order));
        }
        return generation;
    }
    private RequestPostProcessor auth(Long id, String role) {
        return authentication(new UsernamePasswordAuthenticationToken(id, null, List.of(new SimpleGrantedAuthority(role))));
    }
    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock trendClock() {
            return Clock.fixed(Instant.parse("2026-10-04T23:00:00Z"), ZoneOffset.UTC);
        }
    }
}
