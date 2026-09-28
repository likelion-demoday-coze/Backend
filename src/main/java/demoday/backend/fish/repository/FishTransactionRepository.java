package demoday.backend.fish.repository;

import demoday.backend.fish.domain.FishTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface FishTransactionRepository extends JpaRepository<FishTransaction, Long> {

    Page<FishTransaction> findAllByMemberMemberId(Long memberId, Pageable pageable);

    // MySQL REPEATABLE READ에서도 잠금 대기 후 최신 커밋된 거래를 확인한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<FishTransaction> findByIdempotencyKey(String idempotencyKey);
}
