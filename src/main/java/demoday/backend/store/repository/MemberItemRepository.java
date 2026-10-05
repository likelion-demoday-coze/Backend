package demoday.backend.store.repository;

import demoday.backend.store.domain.MemberItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;

public interface MemberItemRepository extends JpaRepository<MemberItem, Long> {
    Optional<MemberItem> findByMemberMemberIdAndItemItemCode(Long memberId, String itemCode);
    Optional<MemberItem> findByMemberMemberIdAndItemItemId(Long memberId, Long itemId);
    @EntityGraph(attributePaths = "item")
    List<MemberItem> findAllByMemberMemberIdAndQuantityGreaterThanOrderByItemItemIdAsc(Long memberId, Integer quantity);
}
