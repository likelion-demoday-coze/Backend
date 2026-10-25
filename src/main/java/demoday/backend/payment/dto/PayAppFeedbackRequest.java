package demoday.backend.payment.dto;

import java.util.Map;

public record PayAppFeedbackRequest(
        String userId,
        String linkKey,
        String linkValue,
        String productName,
        String price,
        String approvedAt,
        String payType,
        String payState,
        String orderNumber,
        String transactionId,
        String feedbackType
) {

    public static PayAppFeedbackRequest from(
            Map<String, String> parameters
    ) {
        return new PayAppFeedbackRequest(
                parameters.get("userid"),
                parameters.get("linkkey"),
                parameters.get("linkval"),
                parameters.get("goodname"),
                parameters.get("price"),
                parameters.get("pay_date"),
                parameters.get("pay_type"),
                parameters.get("pay_state"),
                parameters.get("var1"),
                parameters.get("mul_no"),
                parameters.get("feedbacktype")
        );
    }
}
