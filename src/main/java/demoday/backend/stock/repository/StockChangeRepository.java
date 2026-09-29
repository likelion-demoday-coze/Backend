package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface StockChangeRepository extends JpaRepository<StockChange, Long> {
    Page<StockChange> findAllByMemberMemberId(Long memberId, Pageable pageable);
}
