package demoday.backend.streak.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record StreakPenaltyNotificationAcknowledgmentResponse(
        Long eventId,
        @Schema(description = "최초 알림 확인 시각 (KST). 같은 요청을 반복해도 유지") LocalDateTime acknowledgedAt
) {}
