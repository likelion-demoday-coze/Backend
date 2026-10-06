package demoday.backend.payment.code;

public enum PaymentStatus {

    // 서버에 주문이 생성되고 결제 인증을 기다리는 상태
    READY,

    // 코페이 최종 승인 API 호출을 선점한 상태
    CONFIRMING,

    // 코페이 승인 성공 코드 3001을 확인한 상태
    APPROVED,

    // 결제 승인 정보 저장과 상품 지급까지 완료된 상태
    COMPLETED,

    // 인증 또는 승인이 명확하게 실패한 상태
    FAILED,

    // 승인 여부를 확정할 수 없어 확인이 필요한 상태
    // ex) 3004, 3006, 승인 요청 타임아웃
    UNKNOWN,

    // 결제가 취소된 상태
    CANCELLED
}
