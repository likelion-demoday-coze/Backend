package demoday.backend.trend.service;

import demoday.backend.trend.client.TrendGenerationException;
import demoday.backend.trend.code.TrendGenerationStatus;
import demoday.backend.trend.domain.*;
import demoday.backend.trend.dto.GeneratedTrendContent;
import demoday.backend.trend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trend-storage;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
class TrendContentStorageIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private TrendContentStorageService storage;
    @Autowired private TrendGenerationRepository generations;
    @Autowired private EconomicTrendRepository trends;
    @Autowired private EconomicTermRepository terms;
    @Autowired private TrendReferenceRepository references;
    @Autowired private JdbcTemplate jdbc;
    private TrendGeneration generation;
    private final LocalDate date = LocalDate.of(2026, 10, 5);

    @BeforeEach void setUp() {
        generation = generations.saveAndFlush(TrendGeneration.create(date.atTime(8, 0).plusSeconds(SEQUENCE.incrementAndGet()),
                TrendGenerationStatus.PROCESSING, 1, null));
    }
    @AfterEach void clean() {
        Long id = generation.getTrendGenerationId();
        jdbc.update("delete from economic_term where economic_trend_id in (select economic_trend_id from economic_trend where trend_generation_id=?)", id);
        jdbc.update("delete from trend_reference where economic_trend_id in (select economic_trend_id from economic_trend where trend_generation_id=?)", id);
        jdbc.update("delete from economic_trend where trend_generation_id=?", id);
        jdbc.update("delete from trend_generation where trend_generation_id=?", id);
    }
    @Test void savesAllContentsAndSuccessAndReplayDoesNotDuplicate() {
        storage.saveValidated(generation.getTrendGenerationId(), valid());
        storage.saveValidated(generation.getTrendGenerationId(), valid());
        assertThat(generations.findById(generation.getTrendGenerationId()).orElseThrow().getStatus()).isEqualTo(TrendGenerationStatus.SUCCESS);
        var saved = trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(generation.getTrendGenerationId());
        assertThat(saved).extracting(EconomicTrend::getDisplayOrder).containsExactly(1, 2, 3);
        for (var trend : saved) {
            assertThat(terms.findAllByEconomicTrendEconomicTrendIdOrderByEconomicTermIdAsc(trend.getEconomicTrendId())).hasSize(1);
            assertThat(references.findAllByEconomicTrendEconomicTrendIdOrderByTrendReferenceIdAsc(trend.getEconomicTrendId())).hasSize(1);
        }
    }
    @Test void invalidLastItemRejectsWholeBundle() {
        var invalid = new GeneratedTrendContent(List.of(valid().items().get(0), valid().items().get(1),
                new GeneratedTrendContent.Item("", "요약", List.of(), List.of())));
        assertThatThrownBy(() -> storage.saveValidated(generation.getTrendGenerationId(), invalid))
                .isInstanceOf(TrendGenerationException.class);
        assertThat(trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(generation.getTrendGenerationId())).isEmpty();
        assertProcessing();
    }
    @Test void databaseFailureRollsBackEarlierItemsAndSuccess() {
        // 세 번째 INSERT에서 UNIQUE 충돌을 만들어 앞선 두 이슈의 INSERT도 롤백되는지 확인한다.
        var existing = trends.saveAndFlush(EconomicTrend.create(generation, 3, "기존 테스트 데이터", "요약"));
        assertThatThrownBy(() -> storage.saveValidated(generation.getTrendGenerationId(), valid()))
                .isInstanceOf(DataIntegrityViolationException.class);
        var remaining = trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(generation.getTrendGenerationId());
        assertThat(remaining).extracting(EconomicTrend::getEconomicTrendId).containsExactly(existing.getEconomicTrendId());
        assertThat(terms.count()).isZero(); assertThat(references.count()).isZero();
        assertProcessing();
    }
    @Test void rejectsDuplicateTermsUnsafeUrlsAndFuturePublicationDates() {
        var validator = new TrendContentValidator();
        var original = valid().items().get(0);
        var reference = original.references().get(0);
        for (var invalidItem : List.of(
                new GeneratedTrendContent.Item("이슈", "요약", List.of(original.terms().get(0), original.terms().get(0)), original.references()),
                new GeneratedTrendContent.Item("이슈", "요약", original.terms(), List.of(new GeneratedTrendContent.Reference("출처", "javascript:alert(1)", "기관", date))),
                new GeneratedTrendContent.Item("이슈", "요약", original.terms(), List.of(new GeneratedTrendContent.Reference("출처", reference.url(), "기관", date.plusDays(1)))))) {
            assertThatThrownBy(() -> validator.validate(new GeneratedTrendContent(List.of(invalidItem, valid().items().get(1), valid().items().get(2))), date))
                    .isInstanceOf(TrendGenerationException.class);
        }
    }
    private void assertProcessing() {
        assertThat(generations.findById(generation.getTrendGenerationId()).orElseThrow().getStatus()).isEqualTo(TrendGenerationStatus.PROCESSING);
    }
    private GeneratedTrendContent valid() {
        return new GeneratedTrendContent(java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(i ->
                new GeneratedTrendContent.Item("이슈 " + i, "상세 요약", List.of(new GeneratedTrendContent.Term("금리", "금리 설명")),
                        List.of(new GeneratedTrendContent.Reference("출처", "https://example.com/news/" + i, "기관", date.minusDays(1))))).toList());
    }
}
