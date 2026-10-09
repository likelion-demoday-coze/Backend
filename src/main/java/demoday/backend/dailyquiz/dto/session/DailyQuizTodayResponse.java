package demoday.backend.dailyquiz.dto.session;

public record DailyQuizTodayResponse(
        boolean passApplied,
        int stockReflectionLimit,
        int todayOriginalAnswerCount,
        int usedStockReflectionCount,
        int remainingStockReflectionCount,
        long fishCost
) {
}
