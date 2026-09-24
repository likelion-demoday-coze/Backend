package demoday.backend.store.repository;

import demoday.backend.store.domain.MemberItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberItemRepository extends JpaRepository<MemberItem, Long> {
}
