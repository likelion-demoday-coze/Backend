package demoday.backend.timeattack.repository;

import demoday.backend.timeattack.domain.TimeAttackAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeAttackAnswerRepository extends JpaRepository<TimeAttackAnswer, Long> {
}
