package demoday.backend.dailyquiz.policy;

public final class DailyQuizPolicy {

    public static final int QUESTIONS_PER_SESSION = 5;
    public static final long ENTRY_FISH_COST = 50L;

    public static final int NORMAL_STOCK_REFLECTION_LIMIT = 2;
    public static final int PASS_STOCK_REFLECTION_LIMIT = 3;

    public static final int MIN_STOCK_INCREASE_PERCENT = 1;
    public static final int MAX_STOCK_INCREASE_PERCENT = 10;

    private DailyQuizPolicy() {
    }

    public static int stockReflectionLimit(boolean passApplied) {
        return passApplied
                ? PASS_STOCK_REFLECTION_LIMIT
                : NORMAL_STOCK_REFLECTION_LIMIT;
    }

    public static int stockOpportunityQuestionLimit(boolean passApplied) {
        return stockReflectionLimit(passApplied)
                * QUESTIONS_PER_SESSION;
    }

    public static long entryFishCost(boolean passApplied) {
        return passApplied ? 0L : ENTRY_FISH_COST;
    }
}
