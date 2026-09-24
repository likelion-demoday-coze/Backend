package demoday.backend.streak.repository;

import demoday.backend.streak.domain.StreakRecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StreakRecoveryEventRepository extends JpaRepository<StreakRecoveryEvent, Long> {
}
