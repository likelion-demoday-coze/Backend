package demoday.backend.timeattack.dto.session;

public record TimeAttackTodayResponse(
        int dailyAttemptLimit,
        int usedAttemptCount,
        int remainingAttemptCount
) {
}
