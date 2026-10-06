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

    @Column(name = "last_attempt_slot")
    private Integer lastAttemptSlot;

    @Column(name = "lease_expires_at")
    private LocalDateTime leaseExpiresAt;

    /** 슬롯은 08:00=1, 08:05=2, 08:15=3, 08:30=4다. 실제 호출 횟수와 구분한다. */
    public void beginAttempt(int slot, LocalDateTime leaseUntil) {
        status = TrendGenerationStatus.PROCESSING;
        attemptCount++;
        lastAttemptSlot = slot;
        leaseExpiresAt = leaseUntil;
        lastErrorType = null;
    }

    public boolean ownsAttempt(int attempt, LocalDateTime now) {
        return status == TrendGenerationStatus.PROCESSING && attemptCount == attempt
                && leaseExpiresAt != null && now.isBefore(leaseExpiresAt);
    }

    public void failAttempt(String errorType) {
        status = TrendGenerationStatus.FAILED;
        lastErrorType = errorType;
        leaseExpiresAt = null;
    }

    public void completeSuccessfully() {
        if (status != TrendGenerationStatus.PROCESSING) throw new IllegalStateException("생성 중인 작업만 완료할 수 있습니다.");
        status = TrendGenerationStatus.SUCCESS;
        lastErrorType = null;
        leaseExpiresAt = null;
    }

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
