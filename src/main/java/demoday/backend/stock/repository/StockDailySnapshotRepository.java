package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDailySnapshotRepository extends JpaRepository<StockDailySnapshot, Long> {
}
