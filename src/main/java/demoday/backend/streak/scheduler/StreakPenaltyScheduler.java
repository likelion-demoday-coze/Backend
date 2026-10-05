package demoday.backend.streak.scheduler;

import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.streak.service.StreakPenaltyService;
import demoday.backend.streak.service.StreakRecoveryService;
import demoday.backend.streak.repository.StreakRecoveryEventRepository;
import demoday.backend.streak.code.StreakRecoveryStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class StreakPenaltyScheduler {
    private final MemberRepository members;
    private final StreakPenaltyService penalties;
    private final StreakRecoveryService recoveries;
    private final StreakRecoveryEventRepository events;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void applyOnStartup() {
        applyDuePenalties();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void applyAtMidnight() {
        applyDuePenalties();
    }

    /** 한 회원의 실패로 다른 회원의 처리가 중단되지 않도록 트랜잭션을 분리한다. */
    public void applyDuePenalties() {
        LocalDate yesterday = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul"))).minusDays(1);
        for (Long memberId : events.findOverdueMemberIds(StreakRecoveryStatus.PENDING, yesterday.plusDays(1).atStartOfDay())) {
            try {
                recoveries.expirePending(memberId);
            } catch (Exception exception) {
                log.error("연속 학습 복구 만료 처리 실패. memberId={}", memberId, exception);
            }
        }
        for (Long memberId : members.findStreakPenaltyCandidateIds(MemberStatus.ACTIVE, yesterday)) {
            try {
                penalties.applyDuePenalty(memberId);
            } catch (Exception exception) {
                log.error("연속 학습 하락 처리 실패. memberId={}", memberId, exception);
            }
        }
    }
}
