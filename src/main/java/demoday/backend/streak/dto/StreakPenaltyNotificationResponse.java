package demoday.backend.streak.dto;

import demoday.backend.streak.code.StreakRecoveryStatus;
import demoday.backend.streak.domain.StreakRecoveryEvent;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record StreakPenaltyNotificationResponse(
        Long eventId,
        @Schema(description = "연속 학습이 처음 끊긴 날짜") LocalDate missedDate,
        @Schema(description = "하락을 실제로 반영한 시각 (KST)") LocalDateTime penaltyAppliedAt,
        @Schema(description = "하락 직전 주가. 최신 주가와 다를 수 있음") BigDecimal stockBeforePenalty,
        @Schema(description = "하락 직후 주가. 최신 주가와 다를 수 있음") BigDecimal stockAfterPenalty,
        @Schema(description = "하락 당시 감소한 금액") BigDecimal decreaseAmount,
        @Schema(description = "끊기기 전 연속 학습일") int streakBeforePenalty,
        @Schema(description = "현재 복구 상태. 복구 버튼 조건은 기존 복구 상태 조회 API에서 확인") StreakRecoveryStatus recoveryStatus
) {
    public static StreakPenaltyNotificationResponse from(StreakRecoveryEvent event, LocalDateTime now) {
        return new StreakPenaltyNotificationResponse(event.getStreakRecoveryEventId(), event.getMissedDate(),
                event.getPenaltyAppliedAt(), event.getStockBeforePenalty(), event.getStockAfterPenalty(),
                event.getStockBeforePenalty().subtract(event.getStockAfterPenalty()), event.getStreakBeforePenalty(),
                event.recoveryStatusAt(now));
    }
}
