package demoday.backend.dailyquiz.repository;

import demoday.backend.dailyquiz.domain.DailyQuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyQuizAttemptRepository extends JpaRepository<DailyQuizAttempt, Long> {
}
