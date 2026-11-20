package demoday.backend.timeattack.dto.session;

public record TimeAttackTodayResponse(
        boolean unlimited,
        Integer dailyAttemptLimit,
        int usedAttemptCount,
        Integer remainingAttemptCount
) {
}
