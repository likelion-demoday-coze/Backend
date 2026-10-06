package demoday.backend.streak.dto;

import demoday.backend.streak.code.StreakRecoveryMethod;
import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.domain.StreakRecoveryEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StreakRecoveryResponse(
        Long eventId,
        @Schema(description = "최초 미학습 날짜") LocalDate missedDate,
        StreakRecoveryStatus status,
        @Schema(description = "현재 신청 가능한지. 새 학습을 이전 날짜에 시작했거나 오늘 이미 신청했다면 false. 재고·패스 조건은 신청 시 검사") boolean requestable,
        @Schema(description = "ITEM: 보유 복구권 소모, PASS: 신청 시 활성 패스로 무료") StreakRecoveryMethod method,
        LocalDateTime requestedAt,
        @Schema(description = "신청 당일 정규장 완료 기한. 미신청이면 null (KST)") LocalDateTime expiresAt,
        LocalDateTime recoveredAt,
        @Schema(description = "신청 직후 보유 수량. 패스 무료 신청이면 null") Integer quantityAfterRequest,
        @Schema(description = "복구 완료 직후 주가. 완료 전에는 null이며 최신 주가와 다를 수 있음") BigDecimal stockAfterRecovery,
        @Schema(description = "복구 완료 직후 연속 학습일. 완료 전에는 null") Integer streakAfterRecovery
) {
    public static StreakRecoveryResponse from(StreakRecoveryEvent event, LocalDateTime now, boolean requestable) {
        StreakRecoveryStatus status = event.recoveryStatusAt(now);
        LocalDateTime expiresAt = event.getRequestedAt() == null ? null
                : event.getRequestedAt().toLocalDate().plusDays(1).atStartOfDay();
        return new StreakRecoveryResponse(event.getStreakRecoveryEventId(), event.getMissedDate(), status, requestable,
                event.getRecoveryMethod(), event.getRequestedAt(), expiresAt, event.getRecoveredAt(),
                event.getQuantityAfterRequest(), event.getStockAfterRecovery(), event.getStreakAfterRecovery());
    }
}
