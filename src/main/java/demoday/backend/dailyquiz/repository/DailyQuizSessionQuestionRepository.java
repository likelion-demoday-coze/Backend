package demoday.backend.dailyquiz.repository;

import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyQuizSessionQuestionRepository extends JpaRepository<DailyQuizSessionQuestion, Long> {
}
