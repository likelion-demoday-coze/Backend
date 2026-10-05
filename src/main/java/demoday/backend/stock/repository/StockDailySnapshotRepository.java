package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockDailySnapshotRepository extends JpaRepository<StockDailySnapshot, Long> {

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    List<StockDailySnapshot> findAllBySnapshotDate(LocalDate snapshotDate);

    Optional<StockDailySnapshot> findFirstByMemberMemberIdOrderBySnapshotDateAsc(Long memberId);

    List<StockDailySnapshot> findAllByMemberMemberIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long memberId, LocalDate from, LocalDate to
    );
}
