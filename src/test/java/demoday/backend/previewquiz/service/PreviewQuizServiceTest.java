package demoday.backend.previewquiz.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.previewquiz.code.PreviewQuizErrorCode;
import demoday.backend.previewquiz.dto.PreviewQuizAnswerRequest;
import demoday.backend.previewquiz.dto.PreviewQuizAnswerResponse;
import demoday.backend.previewquiz.dto.PreviewQuizQuestionResponse;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreviewQuizServiceTest {

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private QuizOptionRepository quizOptionRepository;

    @InjectMocks
    private PreviewQuizService previewQuizService;

    @Test
    @DisplayName("PREVIEW 카테고리의 활성 문제 3개와 선택지를 조회한다")
    void getQuestions() {
        QuizQuestion firstQuestion = createQuestion(1L, "문제 1", "해설 1");
        QuizQuestion secondQuestion = createQuestion(2L, "문제 2", "해설 2");
        QuizQuestion thirdQuestion = createQuestion(3L, "문제 3", "해설 3");

        QuizOption firstOption = createOption(11L, firstQuestion, 1, "선택지 1", false);
        QuizOption secondOption = createOption(12L, firstQuestion, 2, "선택지 2", true);
        QuizOption thirdOption = createOption(21L, secondQuestion, 1, "선택지 1", true);
        QuizOption fourthOption = createOption(31L, thirdQuestion, 1, "선택지 1", true);

        when(quizQuestionRepository.findActiveQuestionsByCategory(
                QuizCategory.PREVIEW,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(firstQuestion, secondQuestion, thirdQuestion));
        when(quizOptionRepository
                .findAllByQuestionQuestionIdInOrderByQuestionQuestionIdAscOptionNumberAsc(
                        List.of(1L, 2L, 3L)
                ))
                .thenReturn(List.of(firstOption, secondOption, thirdOption, fourthOption));

        List<PreviewQuizQuestionResponse> result = previewQuizService.getQuestions();

        assertThat(result)
                .extracting(PreviewQuizQuestionResponse::questionId)
                .containsExactly(1L, 2L, 3L);
        assertThat(result.get(0).options())
                .extracting(option -> option.optionId())
                .containsExactly(11L, 12L);
        assertThat(result.get(0).content()).isEqualTo("문제 1");
    }

    @Test
    @DisplayName("활성 맛보기 문제가 3개 미만이면 예외를 반환한다")
    void getQuestionsFailsWhenQuestionsAreInsufficient() {
        when(quizQuestionRepository.findActiveQuestionsByCategory(
                QuizCategory.PREVIEW,
                PageRequest.of(0, 3)
        )).thenReturn(List.of(
                createQuestion(1L, "문제 1", "해설 1"),
                createQuestion(2L, "문제 2", "해설 2")
        ));

        assertThatThrownBy(previewQuizService::getQuestions)
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PreviewQuizErrorCode.INSUFFICIENT_QUESTIONS)
                );

        verifyNoInteractions(quizOptionRepository);
    }

    @Test
    @DisplayName("맛보기 퀴즈 정답을 제출하면 정답과 해설을 반환한다")
    void submitCorrectAnswer() {
        QuizQuestion question = createQuestion(1L, "문제", "해설");
        QuizOption correctOption = createOption(12L, question, 2, "정답", true);
        when(quizQuestionRepository.findActiveQuestion(1L, QuizCategory.PREVIEW))
                .thenReturn(Optional.of(question));
        when(quizOptionRepository.findByOptionIdAndQuestionQuestionId(12L, 1L))
                .thenReturn(Optional.of(correctOption));
        when(quizOptionRepository.findByQuestionQuestionIdAndCorrectTrue(1L))
                .thenReturn(Optional.of(correctOption));

        PreviewQuizAnswerResponse result = previewQuizService.submitAnswer(
                1L,
                new PreviewQuizAnswerRequest(12L)
        );

        assertThat(result.correct()).isTrue();
        assertThat(result.selectedOptionId()).isEqualTo(12L);
        assertThat(result.correctOptionId()).isEqualTo(12L);
        assertThat(result.correctOptionContent()).isEqualTo("정답");
        assertThat(result.explanation()).isEqualTo("해설");
    }

    @Test
    @DisplayName("맛보기 퀴즈 오답을 제출하면 정답 정보와 해설을 반환한다")
    void submitIncorrectAnswer() {
        QuizQuestion question = createQuestion(1L, "문제", "해설");
        QuizOption selectedOption = createOption(11L, question, 1, "오답", false);
        QuizOption correctOption = createOption(12L, question, 2, "정답", true);
        when(quizQuestionRepository.findActiveQuestion(1L, QuizCategory.PREVIEW))
                .thenReturn(Optional.of(question));
        when(quizOptionRepository.findByOptionIdAndQuestionQuestionId(11L, 1L))
                .thenReturn(Optional.of(selectedOption));
        when(quizOptionRepository.findByQuestionQuestionIdAndCorrectTrue(1L))
                .thenReturn(Optional.of(correctOption));

        PreviewQuizAnswerResponse result = previewQuizService.submitAnswer(
                1L,
                new PreviewQuizAnswerRequest(11L)
        );

        assertThat(result.correct()).isFalse();
        assertThat(result.selectedOptionId()).isEqualTo(11L);
        assertThat(result.correctOptionId()).isEqualTo(12L);
        assertThat(result.correctOptionContent()).isEqualTo("정답");
        assertThat(result.explanation()).isEqualTo("해설");
    }

    @Test
    @DisplayName("활성 PREVIEW 문제가 아니면 답안을 제출할 수 없다")
    void submitAnswerFailsWhenQuestionDoesNotExist() {
        when(quizQuestionRepository.findActiveQuestion(1L, QuizCategory.PREVIEW))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> previewQuizService.submitAnswer(
                1L,
                new PreviewQuizAnswerRequest(11L)
        ))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PreviewQuizErrorCode.QUESTION_NOT_FOUND)
                );

        verifyNoInteractions(quizOptionRepository);
    }

    @Test
    @DisplayName("선택지가 해당 문제에 속하지 않으면 답안을 제출할 수 없다")
    void submitAnswerFailsWhenOptionDoesNotBelongToQuestion() {
        QuizQuestion question = createQuestion(1L, "문제", "해설");
        when(quizQuestionRepository.findActiveQuestion(1L, QuizCategory.PREVIEW))
                .thenReturn(Optional.of(question));
        when(quizOptionRepository.findByOptionIdAndQuestionQuestionId(99L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> previewQuizService.submitAnswer(
                1L,
                new PreviewQuizAnswerRequest(99L)
        ))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PreviewQuizErrorCode.OPTION_NOT_FOUND)
                );

        verify(quizOptionRepository, never())
                .findByQuestionQuestionIdAndCorrectTrue(anyLong());
    }

    @Test
    @DisplayName("정답 선택지가 구성되지 않은 문제는 답안 결과를 만들 수 없다")
    void submitAnswerFailsWhenCorrectOptionDoesNotExist() {
        QuizQuestion question = createQuestion(1L, "문제", "해설");
        QuizOption selectedOption = createOption(11L, question, 1, "선택지", false);
        when(quizQuestionRepository.findActiveQuestion(1L, QuizCategory.PREVIEW))
                .thenReturn(Optional.of(question));
        when(quizOptionRepository.findByOptionIdAndQuestionQuestionId(11L, 1L))
                .thenReturn(Optional.of(selectedOption));
        when(quizOptionRepository.findByQuestionQuestionIdAndCorrectTrue(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> previewQuizService.submitAnswer(
                1L,
                new PreviewQuizAnswerRequest(11L)
        ))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PreviewQuizErrorCode.CORRECT_OPTION_NOT_FOUND)
                );
    }

    private QuizQuestion createQuestion(
            Long questionId,
            String content,
            String explanation
    ) {
        QuizQuestion question = QuizQuestion.create(
                QuizCategory.PREVIEW,
                "MULTIPLE_CHOICE",
                content,
                explanation,
                true
        );
        ReflectionTestUtils.setField(question, "questionId", questionId);
        return question;
    }

    private QuizOption createOption(
            Long optionId,
            QuizQuestion question,
            int optionNumber,
            String content,
            boolean correct
    ) {
        QuizOption option = QuizOption.create(
                question,
                optionNumber,
                content,
                correct
        );
        ReflectionTestUtils.setField(option, "optionId", optionId);
        return option;
    }
}
