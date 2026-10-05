package demoday.backend.stock.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.code.StockErrorCode;
import demoday.backend.stock.domain.StockChange;
import demoday.backend.stock.dto.StockChangeResponse;
import demoday.backend.stock.dto.StockHistoryResponse;
import demoday.backend.stock.dto.StockResponse;
import demoday.backend.stock.dto.StockGraphResponse;
import demoday.backend.stock.code.StockGraphPeriod;
import org.springframework.transaction.annotation.Isolation;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.ZoneId;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_HISTORY_DAYS = 30;
    private static final int MAX_HISTORY_DAYS = 366;
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9:_-]{1,100}");

    private final MemberRepository memberRepository;
    private final StockChangeRepository stockChangeRepository;
    private final StockDailySnapshotRepository stockDailySnapshotRepository;
    private final Clock clock;

    /** 같은 DB 스냅샷에서 현재 주가와 그래프를 읽어 동시 변경 중에도 응답 기준을 맞춘다. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StockGraphResponse getGraph(Long memberId, StockGraphPeriod period) {
        if (period == null) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }
        Member member = findActiveMember(memberId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST)).truncatedTo(ChronoUnit.MICROS);
        LocalDateTime joinedAt = member.getCreatedAt();
        boolean estimatedStart = joinedAt == null;
        BigDecimal initialStock = new BigDecimal("100.00");
        if (estimatedStart) {
            // 가입일을 최초 활동일로 단정하지 않는다. 확인 가능한 가장 이른 기록을 표시한다.
            var firstChange = stockChangeRepository.findFirstByMemberMemberIdOrderByCreatedAtAscStockChangeIdAsc(memberId);
            var firstSnapshot = stockDailySnapshotRepository.findFirstByMemberMemberIdOrderBySnapshotDateAsc(memberId);
            joinedAt = now;
            initialStock = member.getCurrentStock();
            if (firstChange.isPresent() && !firstChange.get().getCreatedAt().isAfter(now)) {
                joinedAt = firstChange.get().getCreatedAt();
                initialStock = firstChange.get().getStockBefore();
            }
            if (firstSnapshot.isPresent() && firstSnapshot.get().getSnapshotDate().atStartOfDay().isBefore(joinedAt)) {
                joinedAt = firstSnapshot.get().getSnapshotDate().atStartOfDay();
                initialStock = firstSnapshot.get().getStockValue();
            }
        }
        LocalDateTime from = switch (period) {
            case DAY -> now.toLocalDate().atStartOfDay();
            case WEEK -> now.toLocalDate().minusDays(6).atStartOfDay();
            case ALL -> joinedAt;
        };
        if (from.isBefore(joinedAt)) from = joinedAt;
        List<StockGraphResponse.Point> points = new ArrayList<>();
        BigDecimal startStock;
        if (period == StockGraphPeriod.ALL) {
            startStock = initialStock;
            points.add(new StockGraphResponse.Point(from, startStock));
            var snapshots = stockDailySnapshotRepository.findAllByMemberMemberIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                    memberId, from.toLocalDate(), now.toLocalDate());
            for (var snapshot : snapshots) {
                LocalDateTime time = snapshot.getSnapshotDate().atStartOfDay();
                if (!time.isBefore(from) && !time.isAfter(now)) {
                    points.add(new StockGraphResponse.Point(time, snapshot.getStockValue()));
                }
            }
        } else {
            var changes = stockChangeRepository
                    .findAllByMemberMemberIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscStockChangeIdAsc(
                            memberId, from, now);
            startStock = changes.isEmpty() ? member.getCurrentStock() : changes.get(0).getStockBefore();
            points.add(new StockGraphResponse.Point(from, startStock));
            for (var change : changes) {
                points.add(new StockGraphResponse.Point(change.getCreatedAt(), change.getStockAfter()));
            }
        }
        var currentPoint = new StockGraphResponse.Point(now, member.getCurrentStock());
        if (!points.get(points.size() - 1).equals(currentPoint)) points.add(currentPoint);
        BigDecimal amount = member.getCurrentStock().subtract(startStock);
        BigDecimal rate = startStock.signum() == 0 ? null
                : amount.multiply(BigDecimal.valueOf(100)).divide(startStock, 2, RoundingMode.HALF_UP);
        return new StockGraphResponse(period, from, now, estimatedStart, startStock,
                member.getCurrentStock(), amount, rate, List.copyOf(points));
    }

    /** 호출자의 쓰기 트랜잭션에 참여한다. 재시도는 상위 업무 전체를 감싸야 한다. */
    @Transactional
    public StockChangeResponse changeStock(
            Long memberId, BigDecimal expectedStock, BigDecimal targetStock,
            StockChangeType type, Long referenceId, String idempotencyKey
    ) {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("주가 변경은 읽기 전용 트랜잭션에서 호출할 수 없습니다.");
        }
        BigDecimal before = validateStock(expectedStock);
        BigDecimal after = validateStock(targetStock);
        if (type == null || (referenceId != null && referenceId <= 0)) {
            throw new ProjectException(StockErrorCode.INVALID_CHANGE);
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ProjectException(StockErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        Member member = findActiveMember(memberId, true);
        StockChange existing = stockChangeRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            if (!Objects.equals(existing.getMember().getMemberId(), memberId)
                    || !existing.getIdempotencyKey().equals(idempotencyKey)
                    || existing.getStockBefore().compareTo(before) != 0
                    || existing.getStockAfter().compareTo(after) != 0
                    || existing.getChangeType() != type
                    || !Objects.equals(existing.getReferenceId(), referenceId)) {
                throw new ProjectException(StockErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return StockChangeResponse.from(existing);
        }
        if (member.getCurrentStock().compareTo(before) != 0) {
            throw new ProjectException(StockErrorCode.STALE_STOCK);
        }
        validateChangeRule(memberId, before, after, type, referenceId);
        member.changeStock(after);
        try {
            return StockChangeResponse.from(stockChangeRepository.save(StockChange.create(
                    member, type, before, after, referenceId, idempotencyKey,
                    LocalDateTime.now(clock.withZone(KST)).truncatedTo(ChronoUnit.MICROS)
            )));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE
                        && violation.getSQL() != null
                        && violation.getSQL().toLowerCase(Locale.ROOT).contains("insert into stock_change")) {
                    throw new ProjectException(StockErrorCode.IDEMPOTENCY_CONFLICT);
                }
            }
            throw exception;
        }
    }

    /** 멱등 재요청은 기존 결과를 먼저 반환하며, 신규 변경만 업무별 수치 규칙을 검사한다. */
    private void validateChangeRule(
            Long memberId, BigDecimal before, BigDecimal after, StockChangeType type, Long referenceId
    ) {
        boolean valid = switch (type) {
            case QUIZ_CORRECT -> {
                boolean matches = false;
                for (int percent = 1; percent <= 10; percent++) {
                    BigDecimal expected = before.multiply(BigDecimal.ONE.add(
                            BigDecimal.valueOf(percent).movePointLeft(2)
                    )).setScale(2, RoundingMode.HALF_UP);
                    if (expected.compareTo(after) == 0) {
                        matches = true;
                        break;
                    }
                }
                yield matches;
            }
            case STREAK_PENALTY -> before.multiply(new BigDecimal("0.80"))
                    .setScale(2, RoundingMode.HALF_UP).compareTo(after) == 0;
            case STREAK_RECOVERY -> {
                // 복구 referenceId는 복원 대상인 STREAK_PENALTY 변동 이력의 ID다.
                StockChange penalty = referenceId == null ? null
                        : stockChangeRepository.findById(referenceId).orElse(null);
                yield penalty != null
                        && penalty.getChangeType() == StockChangeType.STREAK_PENALTY
                        && Objects.equals(penalty.getMember().getMemberId(), memberId)
                        && before.divide(new BigDecimal("0.80"), 2, RoundingMode.HALF_UP).compareTo(after) == 0;
            }
            case ADMIN_ADJUSTMENT -> true;
        };
        if (!valid) {
            throw new ProjectException(StockErrorCode.INVALID_CHANGE_VALUE);
        }
    }

    private BigDecimal validateStock(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.TEN.pow(28)) >= 0) {
            throw new ProjectException(MemberErrorCode.INVALID_STOCK_VALUE);
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ProjectException(MemberErrorCode.INVALID_STOCK_VALUE);
        }
    }

    public StockHistoryResponse getHistory(Long memberId, LocalDate from, LocalDate to) {
        findActiveMember(memberId);
        if (from == null && to == null) {
            to = LocalDate.now(clock.withZone(KST));
            from = to.minusDays(DEFAULT_HISTORY_DAYS - 1);
        } else if (from == null || to == null) {
            throw new ProjectException(StockErrorCode.INVALID_HISTORY_PERIOD);
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_HISTORY_DAYS) {
            throw new ProjectException(StockErrorCode.INVALID_HISTORY_PERIOD);
        }
        return StockHistoryResponse.from(from, to,
                stockDailySnapshotRepository.findAllByMemberMemberIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        memberId, from, to
                ));
    }

    public StockResponse getCurrentStock(Long memberId) {
        return new StockResponse(findActiveMember(memberId).getCurrentStock());
    }

    private Member findActiveMember(Long memberId) {
        return findActiveMember(memberId, false);
    }

    private Member findActiveMember(Long memberId, boolean forUpdate) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }
        Member member = (forUpdate ? memberRepository.findByIdForUpdate(memberId) : memberRepository.findById(memberId))
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(StockErrorCode.INACTIVE_MEMBER);
        }
        return member;
    }
}
