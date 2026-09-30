package demoday.backend.previewquiz.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.previewquiz.dto.PreviewQuizAnswerRequest;
import demoday.backend.previewquiz.dto.PreviewQuizAnswerResponse;
import demoday.backend.previewquiz.dto.PreviewQuizQuestionResponse;
import demoday.backend.previewquiz.service.PreviewQuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(
        name = "Preview Quiz",
        description = "비로그인 사용자를 위한 맛보기 퀴즈 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/preview-quizzes")
public class PreviewQuizController {

    private final PreviewQuizService previewQuizService;

    @Operation(
            summary = "맛보기 퀴즈 조회",
            description = """
                    PREVIEW 카테고리에 등록된 활성 문제 3개와 선택지를 조회합니다.
                    답안을 제출하기 전이므로 정답과 해설은 반환하지 않습니다.
                    로그인을 하지 않은 사용자도 호출할 수 있습니다.
                    """
    )
    @GetMapping
    public ApiResponse<List<PreviewQuizQuestionResponse>> getQuestions() {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                previewQuizService.getQuestions()
        );
    }

    @Operation(
            summary = "맛보기 퀴즈 답안 제출",
            description = """
                    특정 맛보기 문제의 답안을 제출합니다.
                    선택한 선택지가 해당 문제에 속하는지 검증한 후
                    정답 여부, 정답 선택지 및 해설을 반환합니다.
                    답안과 결과는 별도로 저장하지 않으며
                    회원 정보, 학습 기록, 주가 및 생선에 반영하지 않습니다.
                    """
    )
    @PostMapping("/{questionId}/answers")
    public ApiResponse<PreviewQuizAnswerResponse> submitAnswer(
            @Parameter(
                    description = "맛보기 퀴즈 문제 ID",
                    example = "1",
                    required = true
            )
            @PathVariable Long questionId,

            @Valid @RequestBody PreviewQuizAnswerRequest request
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                previewQuizService.submitAnswer(
                        questionId,
                        request
                )
        );
    }
}
