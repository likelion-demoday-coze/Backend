package demoday.backend.payment.logging;

public final class PaymentLogSupport {

    private static final int VISIBLE_LENGTH = 4;

    private PaymentLogSupport() {
    }

    public static String maskTransactionId(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return "-";
        }

        String normalized = transactionId.trim();

        if (normalized.length() <= VISIBLE_LENGTH) {
            return "****";
        }

        return "****" + normalized.substring(
                normalized.length() - VISIBLE_LENGTH
        );
    }
}
