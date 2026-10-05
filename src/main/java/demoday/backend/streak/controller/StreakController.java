package demoday.backend.streak.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.streak.dto.StreakResponse;
import demoday.backend.streak.service.StreakService;
import demoday.backend.streak.service.StreakRecoveryService;
import demoday.backend.streak.dto.StreakRecoveryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Streak", description = "정규장 기준 연속 학습")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/streaks")
public class StreakController {
    private final StreakService streakService;
    private final StreakRecoveryService recoveryService;

    @Operation(summary = "내 연속 학습 현황 조회", description = "KST 기준 연속 학습일과 오늘 정규장 학습 완료 여부를 반환합니다. 어제까지 이어지지 않은 기록은 0으로 표시하며 조회로 주가를 변경하지 않습니다. 세션 인증과 ROLE_MEMBER가 필요합니다. 타임어택·출석 보상은 학습 완료에 포함하지 않습니다.")
    @GetMapping("/me")
    public ApiResponse<StreakResponse> getCurrent(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, streakService.getCurrent(memberId));
    }

    @Operation(summary = "내 최근 하락·복구 상태 조회", description = "하락 기록이 없으면 result는 null입니다. 신청 전에는 날짜 제한이 없습니다. 신청 후 다음 날 00:00 KST까지 정규장을 완료해야 복구됩니다. 조회는 상태를 변경하지 않으며, 기한이 지난 대기는 EXPIRED로 표시합니다.")
    @GetMapping("/recovery")
    public ApiResponse<StreakRecoveryResponse> getRecovery(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, recoveryService.getLatest(memberId));
    }

    @Operation(summary = "연속 학습 복구 신청", description = "내 최신 하락 이벤트만 신청할 수 있습니다. 일반 회원은 STREAK_RECOVERY 아이템 1개, 활성 패스 회원은 무료입니다. 같은 이벤트 재요청은 추가 소모하지 않습니다. 오늘 정규장을 먼저 완료해도 신청 즉시 복구됩니다. 하루만 빠졌으면 기존 일수 + 2, 이틀 이상 빠졌으면 2일이며, 주가는 현재 값 / 0.8로 복구합니다. 일일 신청 최대 1회, 미완료 만료 시 환불되지 않습니다. 세션 인증·ROLE_MEMBER 및 CSRF 토큰이 필요합니다.")
    @PostMapping("/recoveries/{eventId}")
    public ApiResponse<StreakRecoveryResponse> requestRecovery(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long eventId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, recoveryService.request(memberId, eventId));
    }
}
