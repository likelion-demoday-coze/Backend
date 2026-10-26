package demoday.backend.payment.client.dto;

public record PayAppPaymentRequest(
        String orderNumber,
        String productName,
        Integer amount,
        String receiverPhone
) {

    public PayAppPaymentRequest {
        if (orderNumber == null || orderNumber.isBlank()) {
            throw new IllegalArgumentException(
                    "주문번호는 필수입니다."
            );
        }

        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException(
                    "상품명은 필수입니다."
            );
        }

        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException(
                    "결제 금액은 1 이상이어야 합니다."
            );
        }

        if (receiverPhone == null
                || !receiverPhone.matches("^01[016789]\\d{7,8}$")) {
            throw new IllegalArgumentException(
                    "휴대전화 번호는 숫자만 입력해야 합니다."
            );
        }
    }
}
