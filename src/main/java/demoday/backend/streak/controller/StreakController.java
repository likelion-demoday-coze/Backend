package demoday.backend.streak.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.streak.dto.StreakResponse;
import demoday.backend.streak.service.StreakService;
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

    @Operation(summary = "내 연속 학습 현황 조회", description = "KST 기준 연속 학습일과 오늘 정규장 학습 완료 여부를 반환합니다. 어제까지 이어지지 않은 기록은 0으로 표시하며 조회로 주가를 변경하지 않습니다. 세션 인증과 ROLE_MEMBER가 필요합니다. 타임어택·출석 보상은 학습 완료에 포함하지 않습니다.")
    @GetMapping("/me")
    public ApiResponse<StreakResponse> getCurrent(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, streakService.getCurrent(memberId));
    }
}
