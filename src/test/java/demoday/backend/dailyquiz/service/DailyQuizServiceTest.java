package demoday.backend.dailyquiz.service;

import demoday.backend.dailyquiz.dto.DailyQuizCategoryResponse;
import demoday.backend.quiz.code.QuizCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DailyQuizServiceTest {

    private final DailyQuizService dailyQuizService = new DailyQuizService();

    @Test
    @DisplayName("와이어프레임 순서대로 데일리 퀴즈 카테고리 8개를 조회한다")
    void getCategories() {
        List<DailyQuizCategoryResponse> result = dailyQuizService.getCategories();

        assertThat(result)
                .hasSize(8)
                .extracting(DailyQuizCategoryResponse::category)
                .containsExactly(QuizCategory.values());
    }
}
