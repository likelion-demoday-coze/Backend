package demoday.backend.trend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Entity
@Table(
        name = "trend_reference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trend_reference_trend_url",
                columnNames = {"economic_trend_id", "url"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TrendReference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trend_reference_id")
    private Long trendReferenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "economic_trend_id", nullable = false)
    private EconomicTrend economicTrend;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(nullable = false, length = 200)
    private String publisher;

    @Column(name = "published_date", nullable = false)
    private LocalDate publishedDate;

    public static TrendReference create(
            EconomicTrend economicTrend,
            String title,
            String url,
            String publisher,
            LocalDate publishedDate
    ) {
        return TrendReference.builder()
                .economicTrend(economicTrend)
                .title(title)
                .url(url)
                .publisher(publisher)
                .publishedDate(publishedDate)
                .build();
    }
}
