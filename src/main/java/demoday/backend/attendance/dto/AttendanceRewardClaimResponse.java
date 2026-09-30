package demoday.backend.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record AttendanceRewardClaimResponse(
        @Schema(description = "수령 처리 기준 날짜 (KST)") LocalDate rewardDate,
        @Schema(description = "이번 요청으로 새로 지급했는지 여부. 같은 날 재요청은 false") boolean newlyClaimed,
        @Schema(description = "이번 요청의 지급량. 최초 100, 이미 수령한 경우 0") long grantedAmount,
        @Schema(description = "이번 요청 처리 시 실제 보유 생선 잔액") long balance
) {
}
