package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface StockChangeRepository extends JpaRepository<StockChange, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockChange> findByIdempotencyKey(String idempotencyKey);

    Page<StockChange> findAllByMemberMemberId(Long memberId, Pageable pageable);
}
