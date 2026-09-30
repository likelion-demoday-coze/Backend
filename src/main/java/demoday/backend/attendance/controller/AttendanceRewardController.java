package demoday.backend.attendance.controller;

import demoday.backend.attendance.dto.AttendanceRewardClaimResponse;
import demoday.backend.attendance.dto.AttendanceRewardStatusResponse;
import demoday.backend.attendance.service.AttendanceRewardService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Attendance Reward", description = "버튼 수령 방식의 일일 출석 보상")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/attendance-rewards")
public class AttendanceRewardController {
    private final AttendanceRewardService attendanceRewardService;

    @Operation(summary = "오늘의 출석 보상 상태 조회", description = "KST 기준 오늘 수령 여부와 활성 패스 여부를 확인합니다. 조회만으로 보상을 지급하지 않습니다.")
    @GetMapping("/today")
    public ApiResponse<AttendanceRewardStatusResponse> getToday(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, attendanceRewardService.getToday(memberId));
    }

    @Operation(summary = "오늘의 출석 보상 수령", description = "KST 하루 한 번 생선 100개를 지급합니다. 같은 날 재요청은 200, newlyClaimed=false, grantedAmount=0입니다. 활성 패스는 신규 지급 불가입니다. 세션 인증과 CSRF 토큰이 필요하며 요청 본문은 없습니다.")
    @PostMapping
    public ApiResponse<AttendanceRewardClaimResponse> claim(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, attendanceRewardService.claim(memberId));
    }
}
