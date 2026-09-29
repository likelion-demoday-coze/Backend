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
import demoday.backend.stock.dto.StockChangePageResponse;
import demoday.backend.stock.dto.StockChangeResponse;
import demoday.backend.stock.dto.StockHistoryResponse;
import demoday.backend.stock.dto.StockResponse;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

    private static final Sort CHANGE_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("stockChangeId")
    );

    private final MemberRepository memberRepository;
    private final StockChangeRepository stockChangeRepository;
    private final StockDailySnapshotRepository stockDailySnapshotRepository;
    private final Clock clock;

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

    public StockChangePageResponse getChanges(Long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ProjectException(StockErrorCode.INVALID_PAGE);
        }
        findActiveMember(memberId);
        return StockChangePageResponse.from(stockChangeRepository.findAllByMemberMemberId(
                memberId, PageRequest.of(page, size, CHANGE_SORT)
        ));
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
