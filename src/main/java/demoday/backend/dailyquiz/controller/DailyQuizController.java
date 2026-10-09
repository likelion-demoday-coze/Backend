package demoday.backend.dailyquiz.controller;

import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerResult;
import demoday.backend.dailyquiz.dto.category.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.dto.result.DailyQuizResultResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizActiveSessionResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionDetailResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizTodayResponse;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Daily Quiz",
        description = "데일리 퀴즈 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/daily-quizzes")
public class DailyQuizController {

    private final DailyQuizService dailyQuizService;

    @Operation(
            summary = "오늘의 정규장 주가 반영 가능 횟수 조회",
            description = """
                    오늘 생성한 정규장 세션을 기준으로 사용 횟수와 남은 횟수를 조회합니다.
                    일반 회원은 하루 2회, 패스 회원은 하루 3회까지 주가에 반영됩니다.
                    """
    )
    @GetMapping("/today")
    public ApiResponse<DailyQuizTodayResponse> getTodayAvailability(
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.getTodayAvailability(memberId)
        );
    }

    @Operation(
            summary = "퀴즈 카테고리 목록 조회",
            description = "데일리 퀴즈에서 선택할 수 있는 경제 카테고리 목록을 조회합니다."
    )
    @GetMapping("/categories")
    public ApiResponse<List<DailyQuizCategoryResponse>> getCategories() {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.getCategories()
        );
    }

    @Operation(
            summary = "데일리 퀴즈 세션 생성",
            description = """
                    선택한 카테고리에서 이전에 풀지 않은 문제 5개로 세션을 생성합니다.
                    활성 패스가 없으면 생선 50개를 차감합니다.
                    생성된 세션은 생성 당일 23시 59분 59초까지 유효합니다.
                    """
    )
    @PostMapping("/sessions")
    public ResponseEntity<ApiResponse<DailyQuizSessionCreateResponse>>
    createSession(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody DailyQuizSessionCreateRequest request
    ) {
        DailyQuizSessionCreateResponse response =
                dailyQuizService.createSession(
                        memberId,
                        request
                );

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
            summary = "진행 중인 데일리 퀴즈 세션 조회",
            description = """
                    회원이 이어 풀 수 있는 진행 중 세션을 조회합니다.
                    진행 중인 세션이 없으면 SESSION_NOT_FOUND 오류를 반환합니다.
                    """
    )
    @GetMapping("/sessions/active")
    public ApiResponse<DailyQuizActiveSessionResponse> getActiveSession(
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.getActiveSession(memberId)
        );
    }

    @Operation(
            summary = "데일리 퀴즈 세션 상세 조회",
            description = """
                    세션에 고정된 문제 5개와 선택지를 조회합니다.
                    기존 답안 제출 여부를 함께 반환하여 이어 풀기에 사용합니다.
                    답안을 제출하기 전에는 정답과 해설을 제공하지 않습니다.
                    """
    )
    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<DailyQuizSessionDetailResponse> getSessionDetail(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.getSessionDetail(
                        memberId,
                        sessionId
                )
        );
    }

    @Operation(
            summary = "데일리 퀴즈 답안 제출",
            description = """
                    원본 문제 또는 오답 재풀이 답안을 제출합니다.
                    attemptType은 ORIGINAL 또는 RETRY입니다.
                    원본 정답은 일일 상승 기회가 남아 있을 때만 주가에 반영됩니다.
                    재풀이는 주가와 학습 횟수에 반영되지 않습니다.
                    """
    )
    @PostMapping(
            "/sessions/{sessionId}/questions/"
                    + "{sessionQuestionId}/answers"
    )
    public ApiResponse<DailyQuizAnswerResult> submitAnswer(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId,
            @PathVariable Long sessionQuestionId,
            @Valid @RequestBody DailyQuizAnswerRequest request
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.submitAnswer(
                        memberId,
                        sessionId,
                        sessionQuestionId,
                        request
                )
        );
    }

    @Operation(
            summary = "데일리 퀴즈 재풀이 건너뛰기",
            description = """
                    원본 문제 5개 제출 후 남은 오답 재풀이를 건너뛰고 세션을 완료합니다.
                    이미 완료된 세션에 같은 요청이 들어오면 기존 결과를 반환합니다.
                    """
    )
    @PostMapping("/sessions/{sessionId}/complete")
    public ApiResponse<DailyQuizResultResponse> completeSession(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.completeSession(
                        memberId,
                        sessionId
                )
        );
    }

    @Operation(
            summary = "데일리 퀴즈 결과 조회",
            description = """
                    원본 문제 5개의 정오답과 해설, 재풀이 결과를 조회합니다.
                    문제별 주가 변화, 최종 수익, 수익률 및 현재 연속 학습일을 반환합니다.
                    완료된 세션 결과는 세션 유효기간이 지난 후에도 조회할 수 있습니다.
                    """
    )
    @GetMapping("/sessions/{sessionId}/result")
    public ApiResponse<DailyQuizResultResponse> getResult(
            @AuthenticationPrincipal Long memberId,
            @PathVariable Long sessionId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.getResult(
                        memberId,
                        sessionId
                )
        );
    }
}
