package demoday.backend.trend.repository;

import demoday.backend.trend.domain.TrendGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import demoday.backend.trend.code.TrendGenerationStatus;
import java.time.LocalDateTime;
import java.util.List;

public interface TrendGenerationRepository extends JpaRepository<TrendGeneration, Long> {
    List<TrendGeneration> findAllByStatusAndGenerationDateBetweenOrderByGenerationDateDesc(
            TrendGenerationStatus status, LocalDateTime from, LocalDateTime to);
}
