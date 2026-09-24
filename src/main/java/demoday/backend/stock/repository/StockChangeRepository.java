package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockChange;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockChangeRepository extends JpaRepository<StockChange, Long> {
}
