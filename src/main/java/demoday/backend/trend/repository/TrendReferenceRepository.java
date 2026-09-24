package demoday.backend.trend.repository;

import demoday.backend.trend.domain.TrendReference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrendReferenceRepository extends JpaRepository<TrendReference, Long> {
}
