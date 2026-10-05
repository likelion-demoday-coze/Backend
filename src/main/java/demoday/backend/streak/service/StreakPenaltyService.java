package demoday.backend.streak.service;

import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.service.StockService;
import demoday.backend.streak.domain.StreakRecoveryEvent;
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
public class StreakPenaltyService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MemberRepository members;
    private final DailyQuizSessionRepository sessions;
    private final StreakRecoveryEventRepository events;
    private final StockService stocks;
    private final TransactionRetryExecutor transactions;
    private final Clock clock;

    /** 스케줄러의 회원 한 명 처리를 가장 바깥 경계에서 재시도한다. */
    public boolean applyDuePenalty(Long memberId) {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        return transactions.execute(() -> applyDuePenaltyInTransaction(memberId, today));
    }

    /** 정규장 업무의 기존 쓰기 트랜잭션에 참여한다. 내부에서 재시도하지 않는다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean applyDuePenaltyInTransaction(Long memberId, LocalDate today) {
        if (today == null) throw new IllegalArgumentException("하락 판정 날짜는 필수입니다.");
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE || member.getCurrentStreak() <= 0) return false;

        LocalDate lastLearningDate = member.getLastLearningDate();
        if (lastLearningDate == null) {
            // 기존 회원은 원본 정규장을 완료한 기록으로 판정한다. 타임어택 기록은 제외한다.
            lastLearningDate = sessions.findFirstByMemberMemberIdAndEndStockIsNotNullOrderByStartedAtDesc(memberId)
                    .map(session -> session.getStartedAt().toLocalDate()).orElse(null);
        }
        if (lastLearningDate == null || !lastLearningDate.isBefore(today.minusDays(1))) return false;

        BigDecimal before = member.getCurrentStock();
        BigDecimal after = before.multiply(new BigDecimal("0.80")).setScale(2, RoundingMode.HALF_UP);
        LocalDate missedDate = lastLearningDate.plusDays(1);
        var event = events.save(StreakRecoveryEvent.create(member, missedDate, member.getCurrentStreak(),
                before, after, LocalDateTime.now(clock.withZone(KST)).truncatedTo(ChronoUnit.MICROS)));
        var change = stocks.changeStock(memberId, before, after, StockChangeType.STREAK_PENALTY,
                event.getStreakRecoveryEventId(), "STREAK_PENALTY:" + memberId + ":" + missedDate);
        event.recordPenaltyStockChange(change.stockChangeId());
        // 다음 자정과 서버 재시작에는 0인 회원을 제외하므로 계속 쉬어도 추가 하락하지 않는다.
        member.breakLearningStreak();
        return true;
    }
}
