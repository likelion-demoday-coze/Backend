package demoday.backend.dailyquiz.repository;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.domain.DailyQuizAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DailyQuizAttemptRepository extends JpaRepository<DailyQuizAttempt, Long> {

    @EntityGraph(attributePaths = "selectedOption")
    Optional<DailyQuizAttempt> findBySessionQuestionSessionQuestionIdAndAttemptType(
            Long sessionQuestionId,
            DailyQuizAttemptType attemptType
    );

    @EntityGraph(attributePaths = {"sessionQuestion", "sessionQuestion.question", "selectedOption"})
    List<DailyQuizAttempt> findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
            Long dailyQuizSessionId,
            DailyQuizAttemptType attemptType
    );

    long countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
            Long dailyQuizSessionId,
            DailyQuizAttemptType attemptType
    );
}
