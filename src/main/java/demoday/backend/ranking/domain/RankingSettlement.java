package demoday.backend.ranking.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(
        name = "ranking_settlement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ranking_settlement_date",
                columnNames = "ranking_date"
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RankingSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ranking_settlement_id")
    private Long rankingSettlementId;

    @Column(name = "ranking_date", nullable = false, updatable = false)
    private LocalDate rankingDate;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private LocalDateTime completedAt;

    public static RankingSettlement complete(
            LocalDate rankingDate,
            LocalDateTime completedAt
    ) {
        return RankingSettlement.builder()
                .rankingDate(rankingDate)
                .completedAt(completedAt)
                .build();
    }
}
