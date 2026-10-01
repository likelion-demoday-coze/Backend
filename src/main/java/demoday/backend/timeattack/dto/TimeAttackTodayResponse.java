package demoday.backend.timeattack.dto;

public record TimeAttackTodayResponse(
        int dailyAttemptLimit,
        int usedAttemptLimit,
        int remainingAttemptCount
) {
}
