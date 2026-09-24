package demoday.backend.trend.domain;

import jakarta.persistence.*;
import lombok.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;

@Getter
@Entity
@Table(
        name = "trend_reference",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_trend_reference_trend_url_hash",
                columnNames = {"economic_trend_id", "url_hash"}
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

    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

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
                .urlHash(hashUrl(url))
                .publisher(publisher)
                .publishedDate(publishedDate)
                .build();
    }

    private static String hashUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("출처 URL은 필수입니다.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
