package demoday.backend.dailyquiz.service;

import demoday.backend.dailyquiz.dto.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.fish.repository.FishTransactionRepository;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DailyQuizServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberPassRepository memberPassRepository;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private DailyQuizSessionRepository dailyQuizSessionRepository;

    @Mock
    private DailyQuizSessionQuestionRepository sessionQuestionRepository;

    @Mock
    private FishTransactionRepository fishTransactionRepository;

    @InjectMocks
    private DailyQuizService dailyQuizService;

    @Test
    @DisplayName("와이어프레임 순서대로 데일리 퀴즈 카테고리 8개를 조회한다")
    void getCategories() {
        List<DailyQuizCategoryResponse> result =
                dailyQuizService.getCategories();

        assertThat(result)
                .hasSize(8)
                .extracting(DailyQuizCategoryResponse::category)
                .containsExactly(QuizCategory.values());
    }
}
