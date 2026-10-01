package demoday.backend.timeattack.dto.session;

public record TimeAttackTodayResponse(
        int dailyAttemptLimit,
        int usedAttemptLimit,
        int remainingAttemptCount
) {
}
