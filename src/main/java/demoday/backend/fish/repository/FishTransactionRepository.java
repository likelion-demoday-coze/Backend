package demoday.backend.fish.repository;

import demoday.backend.fish.domain.FishTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FishTransactionRepository extends JpaRepository<FishTransaction, Long> {
}
