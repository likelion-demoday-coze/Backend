package demoday.backend.streak.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.streak.dto.StreakResponse;
import demoday.backend.streak.service.StreakService;
import demoday.backend.streak.service.StreakRecoveryService;
import demoday.backend.streak.dto.StreakRecoveryResponse;
import demoday.backend.streak.service.StreakPenaltyNotificationService;
import demoday.backend.streak.dto.StreakPenaltyNotificationResponse;
import demoday.backend.streak.dto.StreakPenaltyNotificationAcknowledgmentResponse;
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
    private final StreakPenaltyNotificationService notificationService;

    @Operation(summary = "내 연속 학습 현황 조회", description = "KST 현재 연속학습일·개인 최고 기록·오늘 학습 완료 여부·이번 주 월~일 7일 상태를 반환합니다. 날짜별 상태는 COMPLETED(완료), INCOMPLETE(미완료), RECOVERY_PENDING(복구대기), RECOVERY_COMPLETED(복구완료)입니다. 복구 완료한 쉰 날은 연속성만 연결하고 학습일 수에는 더하지 않습니다. 타임어택·출석은 학습 완료에 포함하지 않습니다. 세션 인증과 ROLE_MEMBER가 필요하며 GET은 상태를 변경하지 않습니다.")
    @GetMapping("/me")
    public ApiResponse<StreakResponse> getCurrent(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, streakService.getCurrent(memberId));
    }

    @Operation(summary = "내 최근 하락·복구 상태 조회", description = "하루만 쉰 경우 하락 당일 다음 자정 전까지만 신청·정규장 완료할 수 있습니다. 이틀 이상 쉰 경우와 기한이 지난 미신청·대기는 EXPIRED로 표시합니다. 하락 기록이 없으면 result는 null이며 조회는 상태를 변경하지 않습니다.")
    @GetMapping("/recovery")
    public ApiResponse<StreakRecoveryResponse> getRecovery(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, recoveryService.getLatest(memberId));
    }

    @Operation(summary = "연속 학습 복구 신청", description = "하루만 쉰 경우 하락 당일에 최신 이벤트를 신청할 수 있습니다. 일반 회원은 복구권 1개 소모, 활성 패스 회원은 무료입니다. 정규장을 먼저 완료했으면 즉시 복구됩니다. 복구 일수는 끊기기 전 일수 + 오늘 1일이며, 쉰 날은 세지 않습니다. 완료 시점 주가 × 1.25로 복구합니다. 일일 신청 최대 1회이고 재요청은 중복 소모하지 않습니다. 당일 정규장을 완료하지 않으면 만료되며 아이템은 반환되지 않습니다. 세션 인증·ROLE_MEMBER·CSRF가 필요합니다.")
    @PostMapping("/recoveries/{eventId}")
    public ApiResponse<StreakRecoveryResponse> requestRecovery(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long eventId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, recoveryService.request(memberId, eventId));
    }

    @Operation(summary = "내 미확인 하락 알림 조회", description = "최신 하락이 미확인일 때만 당시 주가·감소액·연속 학습일과 현재 복구 상태를 반환합니다. 하락이 없거나 최신 하락을 확인했다면 result는 null입니다. GET은 확인 처리하지 않으므로 실제 팝업을 표시한 후 확인 API를 호출해야 합니다. 이전 미확인 알림을 순서대로 다시 노출하지 않습니다. 세션 인증과 ROLE_MEMBER가 필요합니다.")
    @GetMapping("/penalty-notification")
    public ApiResponse<StreakPenaltyNotificationResponse> getPenaltyNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationService.getLatestUnread(memberId));
    }

    @Operation(summary = "하락 알림 확인", description = "실제 팝업 표시 후 받은 eventId로 확인합니다. 본인 이벤트만 확인할 수 있고, 재요청은 최초 확인 시각을 반환합니다. 주가·복구 상태·보유 수량은 변경하지 않습니다. 새 하락이 발생한 뒤 이전 알림을 확인해도 새 알림에는 영향이 없습니다. 세션 인증·ROLE_MEMBER 및 CSRF 토큰이 필요합니다.")
    @PostMapping("/penalty-notifications/{eventId}/acknowledgment")
    public ApiResponse<StreakPenaltyNotificationAcknowledgmentResponse> acknowledgePenaltyNotification(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId, @PathVariable Long eventId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, notificationService.acknowledge(memberId, eventId));
    }
}
