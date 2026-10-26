package demoday.backend.payment.dto;

import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.code.PassType;
import demoday.backend.payment.domain.MemberPass;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MemberPassResponse(

        @Schema(description = "회원 패스 ID", example = "1")
        Long memberPassId,

        @Schema(description = "패스 유형", example = "SEVEN_DAY")
        PassType passType,

        @Schema(description = "조회 시점 기준 패스 상태", example = "ACTIVE")
        PassStatus status,

        @Schema(description = "패스 시작 시각(KST)")
        LocalDateTime startedAt,

        @Schema(description = "패스 만료 시각(KST)")
        LocalDateTime expiresAt,

        @Schema(description = "패스 취소 시각(KST)", nullable = true)
        LocalDateTime cancelledAt
) {

    public static MemberPassResponse from(
            MemberPass memberPass,
            LocalDateTime now
    ) {
        return new MemberPassResponse(
                memberPass.getMemberPassId(),
                memberPass.getPassType(),
                resolveStatus(memberPass, now),
                memberPass.getStartedAt(),
                memberPass.getExpiresAt(),
                memberPass.getCancelledAt()
        );
    }

    private static PassStatus resolveStatus(
            MemberPass memberPass,
            LocalDateTime now
    ) {
        if (memberPass.getStatus() == PassStatus.ACTIVE
                && !now.isBefore(memberPass.getExpiresAt())) {
            return PassStatus.EXPIRED;
        }

        return memberPass.getStatus();
    }
}
