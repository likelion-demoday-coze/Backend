package demoday.backend.streak.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.streak.code.StreakErrorCode;
import demoday.backend.streak.dto.StreakPenaltyNotificationAcknowledgmentResponse;
import demoday.backend.streak.dto.StreakPenaltyNotificationResponse;
import demoday.backend.streak.repository.StreakRecoveryEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class StreakPenaltyNotificationService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MemberRepository members;
    private final StreakRecoveryEventRepository events;
    private final TransactionRetryExecutor transactions;
    private final Clock clock;

    /** 최신 하락만 조회한다. 조회로 확인 상태를 소모하지 않으며 이전 알림을 다시 꺼내지 않는다. */
    @Transactional(readOnly = true)
    public StreakPenaltyNotificationResponse getLatestUnread(Long memberId) {
        findActiveMember(memberId, false);
        LocalDateTime now = now();
        return events.findFirstByMemberMemberIdOrderByMissedDateDesc(memberId)
                .filter(event -> event.getPenaltyNotificationAcknowledgedAt() == null)
                .map(event -> StreakPenaltyNotificationResponse.from(event, now)).orElse(null);
    }

    /** 이벤트 소유권을 검증하고 최초 확인 시각만 저장한다. 회원별 동시 확인은 직렬화한다. */
    public StreakPenaltyNotificationAcknowledgmentResponse acknowledge(Long memberId, Long eventId) {
        if (eventId == null || eventId <= 0) throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        return transactions.execute(() -> {
            findActiveMember(memberId, true);
            var event = events.findByStreakRecoveryEventIdAndMemberMemberId(eventId, memberId)
                    .orElseThrow(() -> new ProjectException(StreakErrorCode.PENALTY_NOTIFICATION_NOT_FOUND));
            event.acknowledgePenaltyNotification(now());
            return new StreakPenaltyNotificationAcknowledgmentResponse(eventId, event.getPenaltyNotificationAcknowledgedAt());
        });
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
