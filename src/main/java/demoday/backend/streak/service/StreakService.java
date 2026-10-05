package demoday.backend.streak.service;

import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.streak.code.StreakErrorCode;
import demoday.backend.streak.dto.StreakResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StreakService {
    private final MemberRepository members;
    private final DailyQuizSessionRepository sessions;
    private final Clock clock;

    /** 조회는 과거 일수를 지우거나 주가를 변경하지 않는다. */
    public StreakResponse getCurrent(Long memberId) {
        if (memberId == null) throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new ProjectException(StreakErrorCode.INACTIVE_MEMBER);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        LocalDate lastLearningDate = member.getLastLearningDate();
        if (lastLearningDate == null) {
            lastLearningDate = sessions.findFirstByMemberMemberIdAndEndStockIsNotNullOrderByStartedAtDesc(memberId)
                    .map(session -> session.getStartedAt().toLocalDate()).orElse(null);
        }
        int streak = lastLearningDate != null && (lastLearningDate.equals(today)
                || lastLearningDate.equals(today.minusDays(1))) ? member.getCurrentStreak() : 0;
        return new StreakResponse(today, streak, today.equals(lastLearningDate), lastLearningDate);
    }
}
