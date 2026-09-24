package demoday.backend.quiz.repository;

import demoday.backend.quiz.domain.QuizOption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizOptionRepository extends JpaRepository<QuizOption, Long> {
}
