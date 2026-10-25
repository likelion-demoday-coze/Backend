package demoday.backend.payment.dto;

import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.code.PassType;
import demoday.backend.payment.domain.MemberPass;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MemberPassHistoryResponse(

        @Schema(description = "회원 패스 ID", example = "1")
        Long memberPassId,

        @Schema(description = "결제 ID", example = "10")
        Long paymentId,

        @Schema(description = "결제 주문번호")
        String orderNumber,

        @Schema(description = "패스 유형", example = "SEVEN_DAY")
        PassType passType,

        @Schema(description = "조회 시점 기준 패스 상태", example = "EXPIRED")
        PassStatus status,

        @Schema(description = "패스 시작 시각(KST)")
        LocalDateTime startedAt,

        @Schema(description = "패스 만료 시각(KST)")
        LocalDateTime expiresAt,

        @Schema(description = "패스 취소 시각(KST)", nullable = true)
        LocalDateTime cancelledAt
) {

    public static MemberPassHistoryResponse from(
            MemberPass memberPass,
            LocalDateTime now
    ) {
        PassStatus status = memberPass.getStatus();

        if (status == PassStatus.ACTIVE
                && !now.isBefore(memberPass.getExpiresAt())) {
            status = PassStatus.EXPIRED;
        }

        return new MemberPassHistoryResponse(
                memberPass.getMemberPassId(),
                memberPass.getPayment().getPaymentId(),
                memberPass.getPayment().getOrderNumber(),
                memberPass.getPassType(),
                status,
                memberPass.getStartedAt(),
                memberPass.getExpiresAt(),
                memberPass.getCancelledAt()
        );
    }
}
