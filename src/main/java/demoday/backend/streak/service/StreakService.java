package demoday.backend.streak.service;

import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.streak.code.StreakErrorCode;
import demoday.backend.streak.dto.StreakResponse;
import demoday.backend.streak.code.StreakLearningStatus;
import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.repository.StreakRecoveryEventRepository;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StreakService {
    private final MemberRepository members;
    private final DailyQuizSessionRepository sessions;
    private final MemberDailyActivityRepository activities;
    private final StreakRecoveryEventRepository events;
    private final Clock clock;

    /** 조회는 과거 일수를 지우거나 주가를 변경하지 않는다. */
    public StreakResponse getCurrent(Long memberId) {
        if (memberId == null) throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new ProjectException(StreakErrorCode.INACTIVE_MEMBER);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        LocalDate today = now.toLocalDate();
        LocalDate lastLearningDate = member.getLastLearningDate();
        if (lastLearningDate == null) {
            lastLearningDate = sessions.findFirstByMemberMemberIdAndEndStockIsNotNullOrderByStartedAtDesc(memberId)
                    .map(session -> session.getStartedAt().toLocalDate()).orElse(null);
        }
        int streak = lastLearningDate != null && (lastLearningDate.equals(today)
                || lastLearningDate.equals(today.minusDays(1))) ? member.getCurrentStreak() : 0;
        var statuses = new TreeMap<LocalDate, StreakLearningStatus>();
        for (var activity : activities.findAllByMemberMemberIdAndActivityDateLessThanEqualOrderByActivityDateAsc(memberId, today)) {
            var status = switch (activity.getLearningStatus()) {
                case COMPLETED -> StreakLearningStatus.COMPLETED;
                case RECOVERED -> StreakLearningStatus.RECOVERY_COMPLETED;
                case NONE -> StreakLearningStatus.INCOMPLETE;
            };
            statuses.put(activity.getActivityDate(), status);
        }
        // 과거 일일 활동 행이 없어도 원본 5문제를 완료한 세션으로 날짜를 보완한다.
        for (var startedAt : sessions.findOriginalCompletedStartTimes(memberId, today.plusDays(1).atStartOfDay()))
            statuses.put(startedAt.toLocalDate(), StreakLearningStatus.COMPLETED);
        int highest = member.getCurrentStreak();
        for (var event : events.findAllByMemberMemberIdOrderByMissedDateAsc(memberId)) {
            if (event.getPenaltyAppliedAt().isAfter(now)) continue;
            highest = Math.max(highest, event.getStreakBeforePenalty());
            if (event.getStreakAfterRecovery() != null) highest = Math.max(highest, event.getStreakAfterRecovery());
            var status = event.recoveryStatusAt(now);
            LocalDate recoveredDay = event.getRecoveredAt() == null ? event.getMissedDate()
                    : event.getRecoveredAt().toLocalDate().minusDays(1);
            if (status == StreakRecoveryStatus.RECOVERED && statuses.get(recoveredDay) != StreakLearningStatus.COMPLETED)
                statuses.put(recoveredDay, StreakLearningStatus.RECOVERY_COMPLETED);
            if (status == StreakRecoveryStatus.PENDING && statuses.get(event.getMissedDate()) != StreakLearningStatus.COMPLETED)
                statuses.put(event.getMissedDate(), StreakLearningStatus.RECOVERY_PENDING);
        }
        int consecutive = 0;
        LocalDate previous = null;
        for (var entry : statuses.entrySet()) {
            boolean bridge = entry.getValue() == StreakLearningStatus.RECOVERY_COMPLETED;
            if (entry.getValue() != StreakLearningStatus.COMPLETED && !bridge) {
                previous = null; consecutive = 0; continue;
            }
            if (previous == null || !previous.plusDays(1).equals(entry.getKey())) consecutive = 0;
            if (!bridge) consecutive++;
            highest = Math.max(highest, consecutive);
            previous = entry.getKey();
        }
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var days = new ArrayList<StreakResponse.Day>(7);
        for (int offset = 0; offset < 7; offset++) {
            LocalDate date = monday.plusDays(offset);
            days.add(new StreakResponse.Day(date, date.isAfter(today) ? StreakLearningStatus.INCOMPLETE
                    : statuses.getOrDefault(date, StreakLearningStatus.INCOMPLETE), date.equals(today), date.isAfter(today)));
        }
        return new StreakResponse(today, streak, today.equals(lastLearningDate), lastLearningDate, highest,
                monday, monday.plusDays(6), List.copyOf(days));
    }
}
