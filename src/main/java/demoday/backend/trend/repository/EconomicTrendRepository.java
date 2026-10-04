package demoday.backend.trend.repository;

import demoday.backend.trend.domain.EconomicTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EconomicTrendRepository extends JpaRepository<EconomicTrend, Long> {
    List<EconomicTrend> findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(Long generationId);
}
