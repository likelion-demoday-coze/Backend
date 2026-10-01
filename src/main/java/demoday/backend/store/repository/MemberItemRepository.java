package demoday.backend.store.repository;

import demoday.backend.store.domain.MemberItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;

public interface MemberItemRepository extends JpaRepository<MemberItem, Long> {
    @EntityGraph(attributePaths = "item")
    List<MemberItem> findAllByMemberMemberIdAndQuantityGreaterThanOrderByItemItemIdAsc(Long memberId, Integer quantity);
}
