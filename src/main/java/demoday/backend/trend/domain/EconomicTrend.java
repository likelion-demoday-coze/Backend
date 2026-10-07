package demoday.backend.trend.domain;

import jakarta.persistence.*;
import lombok.*;
import demoday.backend.trend.code.TrendCategory;

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

    // 기존 콘텐츠는 null일 수 있다. 신규 콘텐츠는 생성 메서드와 검증 단계에서 필수다.
    @Enumerated(EnumType.STRING)
    @Column(length = 40, columnDefinition = "VARCHAR(40)")
    private TrendCategory category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    public static EconomicTrend create(
            TrendGeneration trendGeneration,
            Integer displayOrder,
            String title,
            String summary
    ) {
        return create(trendGeneration, displayOrder, title, summary, TrendCategory.OTHER);
    }

    public static EconomicTrend create(TrendGeneration trendGeneration, Integer displayOrder,
            String title, String summary, TrendCategory category) {
        return EconomicTrend.builder()
                .trendGeneration(trendGeneration)
                .displayOrder(displayOrder)
                .title(title)
                .summary(summary)
                .category(java.util.Objects.requireNonNull(category, "트렌드 카테고리는 필수입니다."))
                .build();
    }
}
