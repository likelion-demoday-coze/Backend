package demoday.backend.store.repository;

import demoday.backend.store.domain.StorePurchase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StorePurchaseRepository extends JpaRepository<StorePurchase, Long> {
    Optional<StorePurchase> findByMemberMemberIdAndRequestId(Long memberId, String requestId);
}
