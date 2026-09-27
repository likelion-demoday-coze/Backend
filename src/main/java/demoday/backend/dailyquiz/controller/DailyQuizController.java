package demoday.backend.dailyquiz.controller;

import demoday.backend.dailyquiz.dto.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.dto.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.dto.DailyQuizSessionCreateResponse;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/daily-quizzes")
public class DailyQuizController {

    private final DailyQuizService dailyQuizService;

    @GetMapping("/categories")
    public ApiResponse<List<DailyQuizCategoryResponse>> getCategories() {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                dailyQuizService.getCategories()
        );
    }

    @PostMapping("/sessions")
    public ResponseEntity<
                ApiResponse<DailyQuizSessionCreateResponse>
                > createSession(
            @AuthenticationPrincipal Long memberId,
            @Valid
            @RequestBody
            DailyQuizSessionCreateRequest request
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
}
