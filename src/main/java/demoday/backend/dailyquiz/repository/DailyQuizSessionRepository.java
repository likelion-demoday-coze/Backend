package demoday.backend.dailyquiz.repository;

import demoday.backend.dailyquiz.domain.DailyQuizSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyQuizSessionRepository extends JpaRepository<DailyQuizSession, Long> {
}
