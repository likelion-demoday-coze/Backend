package demoday.backend.payment.code;

public enum PaymentStatus {

    // 서버에 주문이 생성되고 결제 인증을 기다리는 상태
    READY,

    // 외부 결제창이 발급되어 결제 결과를 기다리는 상태
    PAYMENT_REQUESTED,

    // 페이앱 결제 완료 Feedback을 검증한 상태
    APPROVED,

    // 결제 승인 정보 저장과 상품 지급까지 완료된 상태
    COMPLETED,

    // 페이앱 전액 취소를 요청했으며 결과 확정 전인 상태
    CANCEL_REQUESTED,

    // 페이앱 전액 취소와 패스 회수가 완료된 상태
    REFUNDED,

    // 인증 또는 승인이 명확하게 실패한 상태
    FAILED,

    // 결제가 취소된 상태
    CANCELLED
}
