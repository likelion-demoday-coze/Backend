package demoday.backend.timeattack.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.timeattack.dto.TimeAttackAnswerRequest;
import demoday.backend.timeattack.dto.TimeAttackAnswerResponse;
import demoday.backend.timeattack.dto.TimeAttackCompleteResponse;
import demoday.backend.timeattack.dto.TimeAttackQuestionResponse;
import demoday.backend.timeattack.dto.TimeAttackResultResponse;
import demoday.backend.timeattack.dto.TimeAttackSessionCreateResponse;
import demoday.backend.timeattack.dto.TimeAttackTodayResponse;
import demoday.backend.timeattack.service.TimeAttackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/time-attacks")
public class TimeAttackController {

    private final TimeAttackService timeAttackService;

    // 오늘의 타임어택 참여 가능 횟수 조회
    @GetMapping("/today")
    public ApiResponse<TimeAttackTodayResponse>
    getTodayAvailability(
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                timeAttackService.getTodayAvailability(
                        memberId
                )
        );
    }

    // 타임어택 세션 시작
    @PostMapping("/sessions")
    public ResponseEntity<
            ApiResponse<TimeAttackSessionCreateResponse>>
    createSession(
            @AuthenticationPrincipal Long memberId
    ) {
        TimeAttackSessionCreateResponse response =
                timeAttackService.createSession(memberId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        ApiResponse.onSuccess(
                                GeneralSuccessCode.CREATED,
                                response
                        )
                );
    }

    // 다음 문제 조회
    @GetMapping(
            "/sessions/{sessionId}/questions/next"
    )
    public ApiResponse<TimeAttackQuestionResponse>
    getNextQuestion(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                timeAttackService.getNextQuestion(
                        memberId,
                        sessionId
                )
        );
    }

    // 타임어택 답안 제출
    @PostMapping("/sessions/{sessionId}/answers")
    public ApiResponse<TimeAttackAnswerResponse>
    submitAnswer(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId,
            @Valid @RequestBody
            TimeAttackAnswerRequest request
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                timeAttackService.submitAnswer(
                        memberId,
                        sessionId,
                        request
                )
        );
    }

    // 타임어택 정상 종료
    @PostMapping("/sessions/{sessionId}/complete")
    public ApiResponse<TimeAttackCompleteResponse>
    completeSession(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                timeAttackService.completeSession(
                        memberId,
                        sessionId
                )
        );
    }

    // 타임어택 결과 조회
    @GetMapping("/sessions/{sessionId}/result")
    public ApiResponse<TimeAttackResultResponse>
    getResult(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                timeAttackService.getResult(
                        memberId,
                        sessionId
                )
        );
    }
}
