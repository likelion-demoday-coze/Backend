package demoday.backend.store.repository;

import demoday.backend.store.domain.StoreItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreItemRepository extends JpaRepository<StoreItem, Long> {
    List<StoreItem> findAllByActiveTrueOrderByItemIdAsc();
}
