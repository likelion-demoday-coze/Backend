package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockDailySnapshotRepository extends JpaRepository<StockDailySnapshot, Long> {

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    List<StockDailySnapshot> findAllBySnapshotDate(LocalDate snapshotDate);

    @Query("""
            SELECT s.member.memberId FROM StockDailySnapshot s
            WHERE s.snapshotDate = :date AND s.member.memberId IN :memberIds
            """)
    List<Long> findSavedMemberIds(@Param("date") LocalDate date, @Param("memberIds") List<Long> memberIds);

    Optional<StockDailySnapshot> findFirstByMemberMemberIdOrderBySnapshotDateAsc(Long memberId);

    List<StockDailySnapshot> findAllByMemberMemberIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
            Long memberId, LocalDate from, LocalDate to
    );
}
