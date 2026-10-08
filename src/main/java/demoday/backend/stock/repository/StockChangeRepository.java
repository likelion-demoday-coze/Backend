package demoday.backend.stock.repository;

import demoday.backend.stock.domain.StockChange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

public interface StockChangeRepository extends JpaRepository<StockChange, Long> {
    Optional<StockChange> findFirstByMemberMemberIdOrderByCreatedAtAscStockChangeIdAsc(Long memberId);

    List<StockChange> findAllByMemberMemberIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanEqualOrderByCreatedAtAscStockChangeIdAsc(
            Long memberId, LocalDateTime from, LocalDateTime to
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<StockChange> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        SELECT MAX(
            CASE
                WHEN s.stockBefore >= s.stockAfter
                    THEN s.stockBefore
                ELSE s.stockAfter
            END
        )
        FROM StockChange s
        WHERE s.member.memberId = :memberId
        """)
    Optional<BigDecimal> findHighestStockByMemberId(
            @Param("memberId") Long memberId
    );
}
