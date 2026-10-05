package demoday.backend.streak.service;

import demoday.backend.activity.domain.MemberDailyActivity;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.service.StockService;
import demoday.backend.store.repository.MemberItemRepository;
import demoday.backend.streak.code.StreakErrorCode;
import demoday.backend.streak.code.StreakRecoveryMethod;
import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.domain.StreakRecoveryEvent;
import demoday.backend.streak.dto.StreakRecoveryResponse;
import demoday.backend.streak.repository.StreakRecoveryEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class StreakRecoveryService {
    // 상품명·판매 여부가 바뀌어도 보유 복구권은 이 고정 코드로 식별한다.
    public static final String RECOVERY_ITEM_CODE = "STREAK_RECOVERY";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MemberRepository members;
    private final MemberPassRepository passes;
    private final MemberItemRepository inventory;
    private final MemberDailyActivityRepository activities;
    private final StreakRecoveryEventRepository events;
    private final StreakPenaltyService penalties;
    private final StockService stocks;
    private final TransactionRetryExecutor transactions;
    private final Clock clock;

    /** 하락 기록이 없으면 null. 조회로 재고·주가·DB 상태를 변경하지 않는다. */
    @Transactional(readOnly = true)
    public StreakRecoveryResponse getLatest(Long memberId) {
        Member member = findActiveMember(memberId, false);
        LocalDateTime now = now();
        return events.findFirstByMemberMemberIdOrderByMissedDateDesc(memberId)
                .map(event -> StreakRecoveryResponse.from(event, now, isRequestable(member, event, now))).orElse(null);
    }

    /** 이벤트 ID가 동일하면 신청 결과를 재조회하며 아이템을 중복 소모하지 않는다. */
    public StreakRecoveryResponse request(Long memberId, Long eventId) {
        if (eventId == null || eventId <= 0) throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        RecoveryRequestOutcome outcome = transactions.execute(() -> requestInTransaction(memberId, eventId));
        // 누락된 하락은 먼저 커밋한다. 오래된 이벤트 요청의 409가 그 하락까지 롤백하지 않게 한다.
        if (outcome.error() != null) throw new ProjectException(outcome.error());
        return outcome.response();
    }

    private record RecoveryRequestOutcome(StreakRecoveryResponse response, StreakErrorCode error) {}

    private RecoveryRequestOutcome requestInTransaction(Long memberId, Long eventId) {
        Member member = findActiveMember(memberId, true);
        LocalDateTime now = now();
        expirePendingInTransaction(memberId, now);
        StreakRecoveryEvent event = events.findByStreakRecoveryEventIdAndMemberMemberId(eventId, memberId)
                .orElseThrow(() -> new ProjectException(StreakErrorCode.RECOVERY_NOT_FOUND));
        if (event.getStatus() != StreakRecoveryStatus.AVAILABLE) {
            return new RecoveryRequestOutcome(StreakRecoveryResponse.from(event, now, false), null);
        }
        // 이미 신청한 요청은 위에서 반환한다. 신규 요청만 현재 복구 대상과 일일 제한을 확인한다.
        penalties.applyDuePenaltyInTransaction(memberId, now.toLocalDate());
        var latest = events.findFirstByMemberMemberIdOrderByMissedDateDesc(memberId).orElseThrow();
        if (!latest.getStreakRecoveryEventId().equals(eventId)
                || !canResumeFromEvent(member, event, now.toLocalDate())) {
            return new RecoveryRequestOutcome(null, StreakErrorCode.STALE_RECOVERY);
        }
        if (events.existsByMemberMemberIdAndRequestedAtGreaterThanEqualAndRequestedAtLessThan(
                memberId, now.toLocalDate().atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay())) {
            throw new ProjectException(StreakErrorCode.DAILY_RECOVERY_LIMIT);
        }
        if (passes.findActivePass(memberId, PassStatus.ACTIVE, now).isPresent()) {
            event.requestRecovery(StreakRecoveryMethod.PASS, now, null, null);
        } else {
            var owned = inventory.findByMemberMemberIdAndItemItemCode(memberId, RECOVERY_ITEM_CODE)
                    .orElseThrow(() -> new ProjectException(StreakErrorCode.NO_RECOVERY_ITEM));
            if (owned.getQuantity() <= 0) throw new ProjectException(StreakErrorCode.NO_RECOVERY_ITEM);
            owned.useOne();
            event.requestRecovery(StreakRecoveryMethod.ITEM, now, owned.getMemberItemId(), owned.getQuantity());
        }
        if (now.toLocalDate().equals(member.getLastLearningDate())) completeRecovery(member, event, now);
        return new RecoveryRequestOutcome(StreakRecoveryResponse.from(event, now, false), null);
    }

    /** 원본 5문제 완료와 동일한 트랜잭션에서 호출한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void completeForLearningInTransaction(Long memberId, LocalDateTime learnedAt) {
        Member member = findActiveMember(memberId, true);
        expirePendingInTransaction(memberId, learnedAt);
        events.findFirstByMemberMemberIdAndStatusOrderByMissedDateDesc(memberId, StreakRecoveryStatus.PENDING)
                .ifPresent(event -> completeRecovery(member, event, learnedAt));
    }

    private void completeRecovery(Member member, StreakRecoveryEvent event, LocalDateTime now) {
        BigDecimal before = member.getCurrentStock();
        BigDecimal after = before.divide(new BigDecimal("0.80"), 2, RoundingMode.HALF_UP);
        int restoredStreak = event.getMissedDate().equals(now.toLocalDate().minusDays(1))
                ? Math.addExact(event.getStreakBeforePenalty(), 2) : 2;
        var change = stocks.changeStock(member.getMemberId(), before, after, StockChangeType.STREAK_RECOVERY,
                event.getPenaltyStockChangeId(), "STREAK_RECOVERY:" + event.getStreakRecoveryEventId());
        // 복구한 어제는 학습 활동에서도 RECOVERED로 남겨 이후 연속 판정의 근거로 사용한다.
        LocalDate recoveredDate = now.toLocalDate().minusDays(1);
        var activity = activities.findByMemberMemberIdAndActivityDate(member.getMemberId(), recoveredDate)
                .orElseGet(() -> activities.save(MemberDailyActivity.create(member, recoveredDate)));
        activity.recoverLearning(now);
        member.restoreLearningStreak(restoredStreak, now.toLocalDate());
        event.completeRecovery(change.stockChangeId(), after, restoredStreak, now);
    }

    /** 스케줄러가 회원 한 명의 만료 처리를 독립 트랜잭션으로 실행한다. */
    public void expirePending(Long memberId) {
        transactions.execute(() -> {
            members.findByIdForUpdate(memberId).orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
            expirePendingInTransaction(memberId, now());
            return null;
        });
    }

    private void expirePendingInTransaction(Long memberId, LocalDateTime now) {
        events.findFirstByMemberMemberIdAndStatusOrderByMissedDateDesc(memberId, StreakRecoveryStatus.PENDING)
                .ifPresent(event -> event.expireIfOverdue(now));
    }

    private boolean isRequestable(Member member, StreakRecoveryEvent event, LocalDateTime now) {
        return event.getStatus() == StreakRecoveryStatus.AVAILABLE
                && canResumeFromEvent(member, event, now.toLocalDate())
                && !events.existsByMemberMemberIdAndRequestedAtGreaterThanEqualAndRequestedAtLessThan(
                        member.getMemberId(), now.toLocalDate().atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay());
    }

    /** 오늘 첫 재시작만 허용하고, 전날부터 이어진 새 연속 기록은 덮어쓰지 않는다. */
    private boolean canResumeFromEvent(Member member, StreakRecoveryEvent event, LocalDate today) {
        LocalDate last = member.getLastLearningDate();
        return last == null || last.isBefore(event.getMissedDate())
                || (last.equals(today) && member.getCurrentStreak() == 1);
    }

    private Member findActiveMember(Long memberId, boolean forUpdate) {
        if (memberId == null) throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        Member member = (forUpdate ? members.findByIdForUpdate(memberId) : members.findById(memberId))
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new ProjectException(StreakErrorCode.INACTIVE_MEMBER);
        return member;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KST)).truncatedTo(ChronoUnit.MICROS);
    }
}
