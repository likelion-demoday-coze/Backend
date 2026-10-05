package demoday.backend.stock.scheduler;

import demoday.backend.stock.service.StockSnapshotService;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class StockSnapshotSchedulerTest {
    private final StockSnapshotService snapshots = mock(StockSnapshotService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC);
    @Test void bothTriggersFillThroughKstYesterdayAndContinueAfterOneDateFails() {
        var scheduler = new StockSnapshotScheduler(snapshots, clock, "2026-10-03");
        doThrow(new IllegalStateException("일시 실패")).when(snapshots).capture(LocalDate.of(2026, 10, 3));
        assertThatCode(scheduler::captureOnStartup).doesNotThrowAnyException();
        assertThatCode(scheduler::captureAtMidnight).doesNotThrowAnyException();
        verify(snapshots, times(2)).capture(LocalDate.of(2026, 10, 3));
        verify(snapshots, times(2)).capture(LocalDate.of(2026, 10, 4));
        verifyNoMoreInteractions(snapshots);
    }
    @Test void doesNothingBeforeOperationStartDate() {
        new StockSnapshotScheduler(snapshots, clock, "2026-10-05").capturePendingDates();
        verifyNoInteractions(snapshots);
    }
}
