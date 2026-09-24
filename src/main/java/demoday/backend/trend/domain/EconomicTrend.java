package demoday.backend.trend.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(
        name = "economic_trend",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_economic_trend_generation_order",
                columnNames = {"trend_generation_id", "display_order"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EconomicTrend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "economic_trend_id")
    private Long economicTrendId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trend_generation_id", nullable = false)
    private TrendGeneration trendGeneration;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    public static EconomicTrend create(
            TrendGeneration trendGeneration,
            Integer displayOrder,
            String title,
            String summary
    ) {
        return EconomicTrend.builder()
                .trendGeneration(trendGeneration)
                .displayOrder(displayOrder)
                .title(title)
                .summary(summary)
                .build();
    }
}
