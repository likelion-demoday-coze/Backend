package demoday.backend.trend.repository;

import demoday.backend.trend.domain.EconomicTrend;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.time.LocalDateTime;
import demoday.backend.trend.code.TrendGenerationStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EconomicTrendRepository extends JpaRepository<EconomicTrend, Long> {
    List<EconomicTrend> findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(Long generationId);

    @Query("""
            select t from EconomicTrend t join fetch t.trendGeneration g
            where g.status = :status and g.generationDate >= :from and g.generationDate < :to
            order by g.generationDate desc, t.displayOrder asc
            """)
    List<EconomicTrend> findRecentContent(@Param("status") TrendGenerationStatus status,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);
}
