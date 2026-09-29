package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface StockDailySnapshotRepository extends JpaRepository<StockDailySnapshot, Long> {
    List<StockDailySnapshot> findAllByMemberMemberIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long memberId, LocalDate from, LocalDate to
    );
}
