package demoday.backend.trend.service;

import demoday.backend.trend.client.*;
import demoday.backend.trend.code.*;
import demoday.backend.trend.domain.TrendGeneration;
import demoday.backend.trend.dto.GeneratedTrendContent;
import demoday.backend.trend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:trend-generation;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false",
        "app.economic-trends.enabled=false"
})
@Import(TrendGenerationIntegrationTest.TimeConfig.class)
class TrendGenerationIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    @Autowired private TrendGenerationService service;
    @Autowired private TrendContentStorageService storage;
    @Autowired private TrendGenerationRepository generations;
    @Autowired private EconomicTrendRepository trends;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MovingClock clock;
    @MockitoBean private LinerTrendClient client;

    @BeforeEach void setup() { clock.set("2026-10-04T23:00:00Z"); }
    @AfterEach void clean() {
        var generation = generations.findByGenerationDate(DATE.atTime(8, 0));
        generation.ifPresent(g -> {
            var id = g.getTrendGenerationId();
            jdbc.update("delete from economic_term where economic_trend_id in (select economic_trend_id from economic_trend where trend_generation_id=?)", id);
            jdbc.update("delete from trend_reference where economic_trend_id in (select economic_trend_id from economic_trend where trend_generation_id=?)", id);
            jdbc.update("delete from economic_trend where trend_generation_id=?", id);
            jdbc.update("delete from trend_generation where trend_generation_id=?", id);
        });
    }
    @Test void successfulGenerationIsOnceOnlyAndExternalCallHasNoTransaction() {
        when(client.generate(DATE)).thenAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return content();
        });
        assertThat(service.runSlot(1)).isEqualTo(TrendGenerationService.Result.SUCCESS);
        clock.set("2026-10-04T23:30:00Z");
        assertThat(service.runSlot(4)).isEqualTo(TrendGenerationService.Result.SKIPPED);
        verify(client, times(1)).generate(DATE);
        assertThat(today().getStatus()).isEqualTo(TrendGenerationStatus.SUCCESS);
        assertThat(today().getAttemptCount()).isEqualTo(1);
        assertThat(trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(today().getTrendGenerationId())).hasSize(3);
    }
    @Test void retryScheduleDoesNotRepeatSlotOrExceedFourAttempts() {
        when(client.generate(DATE)).thenThrow(new TrendGenerationException(TrendGenerationFailure.NETWORK));
        assertThat(service.runSlot(2)).isEqualTo(TrendGenerationService.Result.SKIPPED);
        for (int slot = 1; slot <= 4; slot++) {
            clock.set(switch (slot) { case 1 -> "2026-10-04T23:00:00Z"; case 2 -> "2026-10-04T23:05:00Z";
                case 3 -> "2026-10-04T23:15:00Z"; default -> "2026-10-04T23:30:00Z"; });
            assertThat(service.runSlot(slot)).isEqualTo(TrendGenerationService.Result.FAILED);
            assertThat(service.runSlot(slot)).isEqualTo(TrendGenerationService.Result.SKIPPED);
        }
        verify(client, times(4)).generate(DATE);
        assertThat(today().getAttemptCount()).isEqualTo(4);
        assertThat(today().getLastErrorType()).isEqualTo("NETWORK");
        assertThat(today().getStatus()).isEqualTo(TrendGenerationStatus.FAILED);
    }
    @ParameterizedTest
    @EnumSource(value = TrendGenerationFailure.class, names = {"AUTHENTICATION", "INSUFFICIENT_CREDITS", "BAD_REQUEST", "NOT_CONFIGURED"})
    void permanentFailureDoesNotRetry(TrendGenerationFailure failure) {
        when(client.generate(DATE)).thenThrow(new TrendGenerationException(failure));
        service.runSlot(1);
        clock.set("2026-10-04T23:30:00Z");
        assertThat(service.runSlot(4)).isEqualTo(TrendGenerationService.Result.SKIPPED);
        assertThat(today().getLastErrorType()).isEqualTo(failure.name());
        verify(client, times(1)).generate(DATE);
    }
    @Test void invalidContentFailsWithoutPartialSaveThenNextSlotCanSucceed() {
        when(client.generate(DATE)).thenReturn(new GeneratedTrendContent(List.of())).thenReturn(content());
        assertThat(service.runSlot(1)).isEqualTo(TrendGenerationService.Result.FAILED);
        assertThat(today().getLastErrorType()).isEqualTo("INVALID_CONTENT");
        assertThat(trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(today().getTrendGenerationId())).isEmpty();
        clock.set("2026-10-04T23:05:00Z");
        assertThat(service.runSlot(2)).isEqualTo(TrendGenerationService.Result.SUCCESS);
        assertThat(today().getLastErrorType()).isNull();
    }
    @Test void concurrentInitialExecutionsCallLinerOnlyOnce() throws Exception {
        var started = new CountDownLatch(1); var release = new CountDownLatch(1);
        when(client.generate(DATE)).thenAnswer(invocation -> {
            started.countDown(); assertThat(release.await(10, TimeUnit.SECONDS)).isTrue(); return content();
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> service.runSlot(1));
            assertThat(started.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(pool.submit(() -> service.runSlot(1)).get(10, TimeUnit.SECONDS)).isEqualTo(TrendGenerationService.Result.SKIPPED);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(TrendGenerationService.Result.SUCCESS);
        } finally { release.countDown(); pool.shutdownNow(); }
        verify(client, times(1)).generate(DATE);
    }
    @Test void expiredOwnerCannotSaveAndNextSlotTakesOver() {
        var generation = TrendGeneration.create(DATE.atTime(8, 0), TrendGenerationStatus.PENDING, 0, null);
        generation.beginAttempt(1, DATE.atTime(8, 3));
        generations.saveAndFlush(generation);
        clock.set("2026-10-04T23:05:00Z");
        assertThat(storage.saveForAttempt(generation.getTrendGenerationId(), 1, content())).isFalse();
        when(client.generate(DATE)).thenReturn(content());
        assertThat(service.runSlot(2)).isEqualTo(TrendGenerationService.Result.SUCCESS);
        assertThat(storage.saveForAttempt(generation.getTrendGenerationId(), 1, content())).isFalse();
        assertThat(today().getAttemptCount()).isEqualTo(2);
    }
    @Test void startupCatchUpUsesLastDueSlotAndBeforeEightDoesNothing() {
        clock.set("2026-10-04T22:59:59Z");
        assertThat(service.runLatestDueSlot()).isEqualTo(TrendGenerationService.Result.SKIPPED);
        verifyNoInteractions(client);
        clock.set("2026-10-04T23:17:00Z");
        when(client.generate(DATE)).thenReturn(content());
        assertThat(service.runLatestDueSlot()).isEqualTo(TrendGenerationService.Result.SUCCESS);
        assertThat(today().getLastAttemptSlot()).isEqualTo(3);
        assertThat(today().getAttemptCount()).isEqualTo(1);
    }
    private TrendGeneration today() { return generations.findByGenerationDate(DATE.atTime(8, 0)).orElseThrow(); }
    private GeneratedTrendContent content() {
        return new GeneratedTrendContent(java.util.stream.IntStream.rangeClosed(1, 3).mapToObj(i ->
                new GeneratedTrendContent.Item("이슈 " + i, "요약", List.of(new GeneratedTrendContent.Term("금리", "설명")),
                        List.of(new GeneratedTrendContent.Reference("출처", "https://example.com/" + i, "기관", DATE)))).toList());
    }
    static class MovingClock extends Clock {
        private final AtomicReference<Instant> instant; private final ZoneId zone;
        MovingClock() { this(new AtomicReference<>(Instant.parse("2026-10-04T23:00:00Z")), ZoneOffset.UTC); }
        private MovingClock(AtomicReference<Instant> instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        void set(String value) { instant.set(Instant.parse(value)); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MovingClock(instant, zone); }
        @Override public Instant instant() { return instant.get(); }
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary MovingClock generationClock() { return new MovingClock(); } }
}
