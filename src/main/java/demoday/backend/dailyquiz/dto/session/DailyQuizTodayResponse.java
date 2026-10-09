package demoday.backend.dailyquiz.dto.session;

public record DailyQuizTodayResponse(
        boolean passApplied,
        int stockReflectionLimit,
        int usedStockReflectionCount,
        int remainingStockReflectionCount,
        long fishCost
) {
}
