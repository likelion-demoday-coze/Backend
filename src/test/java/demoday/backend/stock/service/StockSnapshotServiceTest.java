package demoday.backend.stock.service;

import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.ranking.repository.RankingQueryRepository;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StockSnapshotServiceTest {
    @Test
    void onlyMissingMembersAreLockedAndCalculatedAndConcurrentSaveIsRechecked() {
        var members = mock(MemberRepository.class);
        var snapshots = mock(StockDailySnapshotRepository.class);
        var closingValues = mock(RankingQueryRepository.class);
        var transactions = mock(TransactionRetryExecutor.class);
        var clock = Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneOffset.UTC);
        var service = new StockSnapshotService(members, snapshots, closingValues, transactions, clock);
        var date = LocalDate.of(2026, 10, 4);
        var boundary = date.plusDays(1).atStartOfDay();
        var candidateIds = List.of(7L, 8L);
        var first = Member.create(7L, "first");
        var second = Member.create(8L, "second");
        ReflectionTestUtils.setField(first, "memberId", 7L);
        ReflectionTestUtils.setField(second, "memberId", 8L);
        when(members.countMissingStockSnapshots(MemberStatus.ACTIVE, boundary, date)).thenReturn(2L);
        when(members.findMissingStockSnapshotMemberIds(MemberStatus.ACTIVE, boundary, date)).thenReturn(candidateIds);
        when(members.findSnapshotCandidatesForUpdate(MemberStatus.ACTIVE, boundary, candidateIds))
                .thenReturn(List.of(first, second));
        when(transactions.execute(any())).thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        // 후보 조회 후 다른 실행이 7번 회원을 먼저 저장한 상황을 재현한다.
        when(snapshots.findSavedMemberIds(date, candidateIds)).thenReturn(List.of(7L));
        var value = mock(demoday.backend.ranking.repository.projection.StockClosingValueRow.class);
        when(value.getMemberId()).thenReturn(8L);
        when(value.getClosingStock()).thenReturn(new java.math.BigDecimal("120"));
        when(closingValues.findStockClosingValues(boundary, List.of(8L))).thenReturn(List.of(value));

        assertThat(service.capture(date)).isEqualTo(1);

        var order = inOrder(members, snapshots, closingValues);
        order.verify(members).findSnapshotCandidatesForUpdate(MemberStatus.ACTIVE, boundary, candidateIds);
        order.verify(snapshots).findSavedMemberIds(date, candidateIds);
        order.verify(closingValues).findStockClosingValues(boundary, List.of(8L));
        verify(members, never()).findAllByStatusAndCreatedBeforeForUpdate(any(), any());
        verify(snapshots, never()).findAllBySnapshotDate(any());
        verify(closingValues, never()).findStockClosingValues(any());
        verify(snapshots).saveAll(argThat(rows -> {
            var additions = new java.util.ArrayList<demoday.backend.stock.domain.StockDailySnapshot>();
            rows.forEach(additions::add);
            return additions.size() == 1 && additions.get(0).getMember().getMemberId().equals(8L);
        }));
    }

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
