package demoday.backend.attendance.dto;

import demoday.backend.attendance.code.AttendanceRewardStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record AttendanceRewardStatusResponse(
        @Schema(description = "조회 기준 날짜 (KST)") LocalDate date,
        @Schema(description = "AVAILABLE: 수령 가능, CLAIMED: 오늘 수령 완료. 패스 여부와 관계없이 동일")
        AttendanceRewardStatus status,
        @Schema(description = "일일 보상 수량", example = "100") long rewardAmount
) {
}
