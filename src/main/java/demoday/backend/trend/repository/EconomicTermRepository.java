package demoday.backend.trend.repository;

import demoday.backend.trend.domain.EconomicTerm;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EconomicTermRepository extends JpaRepository<EconomicTerm, Long> {
}
