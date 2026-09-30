package demoday.backend.previewquiz.controller;

import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:preview-security-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Transactional
class PreviewQuizSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QuizQuestionRepository quizQuestionRepository;

    @Autowired
    private QuizOptionRepository quizOptionRepository;

    private Long questionId;
    private Long correctOptionId;

    @BeforeEach
    void setUp() {
        QuizQuestion question = quizQuestionRepository.save(
                QuizQuestion.create(
                        QuizCategory.PREVIEW,
                        "MULTIPLE_CHOICE",
                        "문제",
                        "해설",
                        true
                )
        );

        QuizOption correctOption = quizOptionRepository.save(
                QuizOption.create(
                        question,
                        1,
                        "정답",
                        true
                )
        );

        questionId = question.getQuestionId();
        correctOptionId = correctOption.getOptionId();
    }

    @Test
    @DisplayName("비로그인 사용자는 CSRF 토큰 없이 맛보기 답안을 제출할 수 있다")
    void submitAnswerWithoutCsrfToken() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/preview-quizzes/{questionId}/answers",
                        questionId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.correct").value(true));
    }

    @Test
    @DisplayName("유효한 CSRF 토큰이 포함된 맛보기 답안 요청도 정상 처리한다")
    void submitAnswerWithCsrfToken() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/preview-quizzes/{questionId}/answers",
                        questionId
                )
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(answerRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.correct").value(true));
    }

    private String answerRequest() {
        return """
                {
                  "selectedOptionId": %d
                }
                """.formatted(correctOptionId);
    }
}
