package demoday.backend.trend.repository;

import demoday.backend.trend.domain.EconomicTrend;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicTrendRepository extends JpaRepository<EconomicTrend, Long> {
}
