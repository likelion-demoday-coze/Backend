package demoday.backend.previewquiz.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.previewquiz.code.PreviewQuizErrorCode;
import demoday.backend.previewquiz.dto.PreviewQuizQuestionResponse;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PreviewQuizService {

    private static final int PREVIEW_QUESTION_COUNT = 3;

    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;

    @Transactional(readOnly = true)
    public List<PreviewQuizQuestionResponse> getQuestions() {
        List<QuizQuestion> questions =
                // Preview 카테고리의 활성 문제 조회
                quizQuestionRepository.findActiveQuestionsByCategory(
                        QuizCategory.PREVIEW,
                        PageRequest.of(0, PREVIEW_QUESTION_COUNT)
                );

        // 문제 개수 검증
        if (questions.size() < PREVIEW_QUESTION_COUNT) {
            throw new ProjectException(
                    PreviewQuizErrorCode.INSUFFICIENT_QUESTIONS
            );
        }

        // 조회한 문제 ID 추출
        List<Long> questionIds = questions.stream()
                .map(QuizQuestion::getQuestionId)
                .toList();

        Map<Long, List<QuizOption>> optionsByQuestionId =
                // 세 문제의 선택지 일괄 조회
                quizOptionRepository
                        .findAllByQuestionQuestionIdInOrderByQuestionQuestionIdAscOptionNumberAsc(
                                questionIds
                        )
                        .stream()
                        // 선택지를 문제 ID별로 그룹화
                        .collect(Collectors.groupingBy(
                                option -> option.getQuestion().getQuestionId()
                        ));

        // 문제와 선택지 조회 DTO로 변환
        return questions.stream()
                .map(question -> PreviewQuizQuestionResponse.of(
                        question,
                        optionsByQuestionId.getOrDefault(
                                question.getQuestionId(),
                                List.of()
                        )
                ))
                .toList();
    }
}
