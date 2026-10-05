package demoday.backend.stock.service;

import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.ranking.repository.RankingQueryRepository;
import demoday.backend.ranking.repository.projection.StockClosingValueRow;
import demoday.backend.stock.domain.StockDailySnapshot;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockSnapshotService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MemberRepository members;
    private final StockDailySnapshotRepository snapshots;
    private final RankingQueryRepository closingValues;
    private final TransactionRetryExecutor transactions;
    private final Clock clock;

    /** 해당 날짜의 마감값을 확정한다. 기존 값은 유지하고 누락된 회원만 추가한다. */
    public int capture(LocalDate date) {
        if (date == null || !date.isBefore(LocalDate.now(clock.withZone(KST))))
            throw new IllegalArgumentException("스냅샷은 마감된 과거 날짜만 저장할 수 있습니다.");
        var closedAt = date.plusDays(1).atStartOfDay();
        // 완료된 날짜는 집계 조회만 수행하고 회원 잠금·엔티티 적재를 생략한다.
        if (members.countMissingStockSnapshots(MemberStatus.ACTIVE, closedAt, date) == 0) return 0;
        return transactions.execute(() -> {
            // 모든 호출자가 같은 회원 ID 순서로 잠가 동시 생성·주가 변경과 직렬화한다.
            var eligible = members.findAllByStatusAndCreatedBeforeForUpdate(MemberStatus.ACTIVE, closedAt);
            var savedMemberIds = snapshots.findAllBySnapshotDate(date).stream()
                    .map(snapshot -> snapshot.getMember().getMemberId()).collect(Collectors.toSet());
            var missing = eligible.stream().filter(member -> !savedMemberIds.contains(member.getMemberId())).toList();
            if (missing.isEmpty()) return 0;
            // 자정 이후 첫 변동의 stockBefore가 자정 직전 값이다. 이후 변동이 없으면 현재 값이다.
            var values = closingValues.findStockClosingValues(closedAt).stream()
                    .collect(Collectors.toMap(StockClosingValueRow::getMemberId, Function.identity()));
            var additions = missing.stream().map(member -> {
                var value = values.get(member.getMemberId());
                if (value == null || value.getClosingStock() == null)
                    throw new IllegalStateException("회원 마감 주가를 조회할 수 없습니다.");
                return StockDailySnapshot.create(member, date, value.getClosingStock());
            }).toList();
            snapshots.saveAll(additions);
            snapshots.flush();
            return additions.size();
        });
    }
}
