package demoday.backend.timeattack.repository;

import demoday.backend.timeattack.domain.TimeAttackSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeAttackSessionRepository extends JpaRepository<TimeAttackSession, Long> {
}
