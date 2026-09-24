package demoday.backend.trend.domain;

import demoday.backend.trend.code.TrendGenerationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "trend_generation")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TrendGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "trend_generation_id")
    private Long trendGenerationId;

    @Column(name = "generation_date", nullable = false, unique = true)
    private LocalDateTime generationDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrendGenerationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "last_error_type", length = 100)
    private String lastErrorType;

    public static TrendGeneration create(
            LocalDateTime generationDate,
            TrendGenerationStatus status,
            Integer attemptCount,
            String lastErrorType
    ) {
        return TrendGeneration.builder()
                .generationDate(generationDate)
                .status(status)
                .attemptCount(attemptCount)
                .lastErrorType(lastErrorType)
                .build();
    }
}
