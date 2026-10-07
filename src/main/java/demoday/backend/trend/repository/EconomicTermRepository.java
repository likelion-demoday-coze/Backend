package demoday.backend.trend.repository;

import demoday.backend.trend.domain.EconomicTerm;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EconomicTermRepository extends JpaRepository<EconomicTerm, Long> {
    List<EconomicTerm> findAllByEconomicTrendEconomicTrendIdOrderByEconomicTermIdAsc(Long trendId);
}
