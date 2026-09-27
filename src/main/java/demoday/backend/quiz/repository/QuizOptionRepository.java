package demoday.backend.quiz.repository;

import demoday.backend.quiz.domain.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {

    List<QuizOption> findAllByQuestionQuestionIdInOrderByQuestionQuestionIdAscOptionNumberAsc(
            Collection<Long> questionIds
    );

    Optional<QuizOption> findByOptionIdAndQuestionQuestionId(
            Long optionId,
            Long questionId
    );

    Optional<QuizOption> findByQuestionQuestionIdAndCorrectTrue(Long questionId);
}
