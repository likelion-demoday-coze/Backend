package demoday.backend.store.domain;

import demoday.backend.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(
        name = "member_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_item_member_item",
                columnNames = {"member_id", "item_id"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_item_id")
    private Long memberItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private StoreItem item;

    @Column(nullable = false)
    private Integer quantity;

    public static MemberItem create(Member member, StoreItem item, Integer quantity) {
        return MemberItem.builder()
                .member(member)
                .item(item)
                .quantity(quantity)
                .build();
    }
}
