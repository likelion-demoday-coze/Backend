package demoday.backend.trend.repository;

import demoday.backend.trend.domain.TrendGeneration;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendGenerationRepository extends JpaRepository<TrendGeneration, Long> {
}
