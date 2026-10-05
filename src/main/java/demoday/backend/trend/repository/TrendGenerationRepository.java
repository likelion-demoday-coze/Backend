package demoday.backend.trend.repository;

import demoday.backend.trend.domain.TrendGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import demoday.backend.trend.code.TrendGenerationStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface TrendGenerationRepository extends JpaRepository<TrendGeneration, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from TrendGeneration g where g.trendGenerationId = :id")
    Optional<TrendGeneration> findByIdForUpdate(@Param("id") Long id);
    List<TrendGeneration> findAllByStatusAndGenerationDateBetweenOrderByGenerationDateDesc(
            TrendGenerationStatus status, LocalDateTime from, LocalDateTime to);
}
