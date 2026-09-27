package demoday.backend.dailyquiz.service;

import demoday.backend.dailyquiz.dto.DailyQuizCategoryResponse;
import demoday.backend.quiz.code.QuizCategory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class DailyQuizService {

    public List<DailyQuizCategoryResponse> getCategories() {
        return Arrays.stream(QuizCategory.values())
                .map(DailyQuizCategoryResponse::from)
                .toList();
    }
}
