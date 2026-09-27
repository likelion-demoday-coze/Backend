package demoday.backend.dailyquiz.repository;

import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyQuizSessionQuestionRepository extends JpaRepository<DailyQuizSessionQuestion, Long> {

    @EntityGraph(attributePaths = "question")
    List<DailyQuizSessionQuestion> findAllByDailyQuizSessionDailyQuizSessionIdOrderByQuestionQuestionIdAsc(
            Long dailyQuizSessionId
    );

    @EntityGraph(attributePaths = "question")
    Optional<DailyQuizSessionQuestion> findBySessionQuestionIdAndDailyQuizSessionDailyQuizSessionId(
            Long sessionQuestionId,
            Long dailyQuizSessionId
    );
}
