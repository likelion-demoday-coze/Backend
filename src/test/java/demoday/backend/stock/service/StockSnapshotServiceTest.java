package demoday.backend.stock.service;

import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.ranking.repository.RankingQueryRepository;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StockSnapshotServiceTest {
    @Test
    void completedDateSkipsTransactionLocksAndEntityLoading() {
        var members = mock(MemberRepository.class);
        var snapshots = mock(StockDailySnapshotRepository.class);
        var closingValues = mock(RankingQueryRepository.class);
        var transactions = mock(TransactionRetryExecutor.class);
        var clock = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneOffset.UTC);
        var service = new StockSnapshotService(members, snapshots, closingValues, transactions, clock);
        var date = LocalDate.of(2026, 10, 4);
        when(members.countMissingStockSnapshots(MemberStatus.ACTIVE,
                date.plusDays(1).atStartOfDay(), date)).thenReturn(0L);

        assertThat(service.capture(date)).isZero();

        verify(members).countMissingStockSnapshots(MemberStatus.ACTIVE, date.plusDays(1).atStartOfDay(), date);
        verifyNoMoreInteractions(members);
        verifyNoInteractions(snapshots, closingValues, transactions);
    }
}
