package demoday.backend.trend.repository;

import demoday.backend.trend.domain.TrendReference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TrendReferenceRepository extends JpaRepository<TrendReference, Long> {
    List<TrendReference> findAllByEconomicTrendEconomicTrendIdOrderByTrendReferenceIdAsc(Long trendId);
}
