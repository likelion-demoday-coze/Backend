package demoday.backend.previewquiz.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.previewquiz.dto.PreviewQuizAnswerRequest;
import demoday.backend.previewquiz.dto.PreviewQuizAnswerResponse;
import demoday.backend.previewquiz.dto.PreviewQuizQuestionResponse;
import demoday.backend.previewquiz.service.PreviewQuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/preview-quizzes")
public class PreviewQuizController {

    private final PreviewQuizService previewQuizService;

    @GetMapping
    public ApiResponse<List<PreviewQuizQuestionResponse>> getQuestions() {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                previewQuizService.getQuestions()
        );
    }

    @PostMapping("/{questionId}/answers")
    public ApiResponse<PreviewQuizAnswerResponse> submitAnswer(
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
