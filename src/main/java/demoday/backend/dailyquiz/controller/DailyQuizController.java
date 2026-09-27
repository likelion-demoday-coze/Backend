package demoday.backend.dailyquiz.controller;

import demoday.backend.dailyquiz.dto.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
