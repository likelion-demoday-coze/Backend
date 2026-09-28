package demoday.backend.fish.code;

public enum FishTransactionType {
    ATTENDANCE_REWARD,
    RANKING_REWARD,
    DAILY_QUIZ_COST,
    TIME_ATTACK_COST,
    ITEM_PURCHASE,
    PAID_CHARGE,
    PAYMENT_CANCEL,
    ADMIN_ADJUSTMENT;

    public boolean allowsCredit() {
        return switch (this) {
            case ATTENDANCE_REWARD, RANKING_REWARD, PAID_CHARGE, ADMIN_ADJUSTMENT -> true;
            default -> false;
        };
    }

    public boolean allowsDebit() {
        return switch (this) {
            case DAILY_QUIZ_COST, TIME_ATTACK_COST, ITEM_PURCHASE, PAYMENT_CANCEL,
                 ADMIN_ADJUSTMENT -> true;
            default -> false;
        };
    }
}
