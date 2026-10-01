package demoday.backend.timeattack.service;

import demoday.backend.activity.domain.MemberDailyActivity;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.timeattack.dto.TimeAttackTodayResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TimeAttackService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final int DAILY_ATTEMPT_LIMIT = 3;

    private final MemberDailyActivityRepository memberDailyActivityRepository;

    public TimeAttackTodayResponse getTodayAvailability(
            Long memberId
    ) {
        LocalDate today = LocalDate.now(KST);

        int usedAttemptCount = memberDailyActivityRepository
                .findByMemberMemberIdAndActivityDate(
                        memberId,
                        today
                )
                .map(MemberDailyActivity::getTimeAttackAttemptCount)
                .orElse(0);

        int remainingAttemptCount = Math.max(
                DAILY_ATTEMPT_LIMIT - usedAttemptCount,
                0
        );

        return new TimeAttackTodayResponse(
                DAILY_ATTEMPT_LIMIT,
                usedAttemptCount,
                remainingAttemptCount
        );
    }
}