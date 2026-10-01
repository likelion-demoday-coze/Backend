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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(
        name = "Time Attack",
        description = "1분 동안 경제 퀴즈를 푸는 타임어택 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/time-attacks")
public class TimeAttackController {

    private final TimeAttackService timeAttackService;

    @Operation(
            summary = "오늘의 타임어택 참여 가능 횟수 조회",
            description = """
                    오늘 사용한 타임어택 참여 횟수와 남은 횟수를 조회합니다.
                    일반 회원과 패스 회원 모두 하루 최대 3회 참여할 수 있습니다.
                    """
    )
    @GetMapping("/today")
    public ApiResponse<TimeAttackTodayResponse>
    getTodayAvailability(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                timeAttackService.getTodayAvailability(
                        memberId
                )
        );
    }

    @Operation(
            summary = "타임어택 세션 시작",
            description = """
                    제한 시간이 1분인 타임어택 세션을 생성합니다.
                    세션 생성 시 오늘의 참여 횟수가 즉시 1회 증가합니다.
                    활성 패스가 없는 회원은 생선 100개가 차감되며,
                    패스 회원은 생선 차감 없이 참여할 수 있습니다.
                    """
    )
    @PostMapping("/sessions")
    public ResponseEntity<
            ApiResponse<TimeAttackSessionCreateResponse>>
    createSession(
            @Parameter(hidden = true)
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

    @Operation(
            summary = "타임어택 다음 문제 조회",
            description = """
                    진행 중인 세션에서 아직 답하지 않은 다음 문제를 조회합니다.
                    전체 활성 문제 중 PREVIEW 카테고리를 제외하고 출제합니다.
                    동일 세션에서는 같은 문제가 중복 출제되지 않으며,
                    정답과 해설은 반환하지 않습니다.
                    제한 시간이 종료된 세션에서는 문제를 조회할 수 없습니다.
                    """
    )
    @GetMapping(
            "/sessions/{sessionId}/questions/next"
    )
    public ApiResponse<TimeAttackQuestionResponse>
    getNextQuestion(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(
                    description = "타임어택 세션 ID",
                    example = "1",
                    required = true
            )
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

    @Operation(
            summary = "타임어택 답안 제출",
            description = """
                    현재 출제된 문제의 답안을 제출합니다.
                    정답 여부와 정답 선택지, 누적 정답 수 및 현재 콤보를 반환합니다.
                    동일 문제에 대한 중복 제출과 현재 문제가 아닌 문제의 제출은 허용하지 않습니다.
                    타임어택 답안은 주가와 일반 문제 풀이 이력에 반영되지 않습니다.
                    """
    )
    @PostMapping("/sessions/{sessionId}/answers")
    public ApiResponse<TimeAttackAnswerResponse>
    submitAnswer(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(
                    description = "타임어택 세션 ID",
                    example = "1",
                    required = true
            )
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

    @Operation(
            summary = "타임어택 정상 종료",
            description = """
                    제한 시간 1분이 지난 타임어택 세션을 정상 완료 처리합니다.
                    총 답안 수, 정답 수, 오답 수 및 최대 콤보를 반환합니다.
                    정상 완료된 세션만 학습 완료 및 연속 학습 기록에 반영됩니다.
                    동일한 완료 요청이 반복되면 기존 완료 결과를 반환합니다.
                    """
    )
    @PostMapping("/sessions/{sessionId}/complete")
    public ApiResponse<TimeAttackCompleteResponse>
    completeSession(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(
                    description = "타임어택 세션 ID",
                    example = "1",
                    required = true
            )
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

    @Operation(
            summary = "타임어택 결과 조회",
            description = """
                    정상 완료된 타임어택 세션의 최종 결과를 조회합니다.
                    총 정답·오답 수, 최대 콤보와 문제별 선택 답안,
                    정답 선택지 및 해설을 반환합니다.
                    진행 중이거나 만료된 세션의 결과는 조회할 수 없습니다.
                    """
    )
    @GetMapping("/sessions/{sessionId}/result")
    public ApiResponse<TimeAttackResultResponse>
    getResult(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(
                    description = "타임어택 세션 ID",
                    example = "1",
                    required = true
            )
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
