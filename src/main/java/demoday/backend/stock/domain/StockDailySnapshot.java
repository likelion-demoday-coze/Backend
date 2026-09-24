package demoday.backend.stock.domain;

import demoday.backend.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "stock_daily_snapshot",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_stock_daily_snapshot_member_date",
                columnNames = {"member_id", "snapshot_date"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class StockDailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_snapshot_id")
    private Long stockSnapshotId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "stock_value", nullable = false, precision = 30, scale = 2)
    private BigDecimal stockValue;

    public static StockDailySnapshot create(
            Member member,
            LocalDate snapshotDate,
            BigDecimal stockValue
    ) {
        return StockDailySnapshot.builder()
                .member(member)
                .snapshotDate(snapshotDate)
                .stockValue(stockValue)
                .build();
    }
}
