package demoday.backend.trend.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(
        name = "economic_term",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_economic_term_trend_name",
                columnNames = {"economic_trend_id", "name"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EconomicTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "economic_term_id")
    private Long economicTermId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "economic_trend_id", nullable = false)
    private EconomicTrend economicTrend;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    public static EconomicTerm create(
            EconomicTrend economicTrend,
            String name,
            String description
    ) {
        return EconomicTerm.builder()
                .economicTrend(economicTrend)
                .name(name)
                .description(description)
                .build();
    }
}
