package demoday.backend.trend.service;

import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.trend.client.LinerTrendClient;
import demoday.backend.trend.client.TrendGenerationException;
import demoday.backend.trend.code.TrendGenerationFailure;
import demoday.backend.trend.code.TrendGenerationStatus;
import demoday.backend.trend.domain.TrendGeneration;
import demoday.backend.trend.repository.TrendGenerationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import java.time.*;

@Slf4j
@Service
public class TrendGenerationService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final TrendGenerationRepository generations;
    private final TransactionRetryExecutor transactions;
    private final LinerTrendClient client;
    private final TrendContentStorageService storage;
    private final Clock clock;
    private final long leaseSeconds;

    public TrendGenerationService(TrendGenerationRepository generations, TransactionRetryExecutor transactions,
            LinerTrendClient client, TrendContentStorageService storage, Clock clock,
            @Value("${liner.timeout-seconds:120}") long timeoutSeconds) {
        this.generations = generations; this.transactions = transactions; this.client = client;
        this.storage = storage; this.clock = clock;
        this.leaseSeconds = Math.addExact(timeoutSeconds, 60);
    }

    public enum Result { SUCCESS, FAILED, SKIPPED }
    private record Claim(Long id, int attempt) {}

    /** 슬롯을 선점한 서버만 외부 API를 한 번 호출한다. 재시도는 다음 예약 슬롯에서만 한다. */
    public Result runSlot(int slot) {
        if (slot < 1 || slot > 4) throw new IllegalArgumentException("생성 슬롯은 1~4입니다.");
        LocalDateTime captured = now();
        if (slot > latestDueSlot(captured.toLocalTime())) return Result.SKIPPED;
        LocalDate date = captured.toLocalDate();
        Long id = ensureGeneration(date);
        Claim claim = transactions.execute(() -> {
            var generation = generations.findByIdForUpdate(id).orElseThrow();
            LocalDateTime lockedAt = now();
            if (!lockedAt.toLocalDate().equals(date)) return null;
            if (generation.getStatus() == TrendGenerationStatus.SUCCESS) return null;
            if (generation.getStatus() == TrendGenerationStatus.PROCESSING
                    && generation.getLeaseExpiresAt() != null && lockedAt.isBefore(generation.getLeaseExpiresAt())) return null;
            if (generation.getStatus() == TrendGenerationStatus.PROCESSING)
                generation.failAttempt(TrendGenerationFailure.NETWORK.name());
            if (generation.getAttemptCount() >= 4
                    || (generation.getLastAttemptSlot() != null && generation.getLastAttemptSlot() >= slot)) return null;
            if (generation.getStatus() == TrendGenerationStatus.FAILED && !isRetryable(generation.getLastErrorType())) return null;
            generation.beginAttempt(slot, lockedAt.plusSeconds(leaseSeconds));
            return new Claim(id, generation.getAttemptCount());
        });
        if (claim == null) return Result.SKIPPED;
        try {
            var content = client.generate(date); // DB 트랜잭션과 회원 잠금을 가진 채 기다리지 않는다.
            if (storage.saveForAttempt(claim.id(), claim.attempt(), content)) return Result.SUCCESS;
            recordFailure(claim, TrendGenerationFailure.NETWORK);
            return Result.SKIPPED;
        } catch (TrendGenerationException exception) {
            recordFailure(claim, exception.getFailure());
            return Result.FAILED;
        } catch (TransientDataAccessException | RecoverableDataAccessException | DataAccessResourceFailureException exception) {
            // 잠금 충돌·연결 장애 등 인프라 오류는 다음 예약 슬롯에서 재시도한다.
            recordFailure(claim, TrendGenerationFailure.STORAGE_UNAVAILABLE);
            return Result.FAILED;
        } catch (RuntimeException exception) {
            // SQL·외부 응답 원문은 로그와 오류 유형에 보존하지 않는다.
            recordFailure(claim, TrendGenerationFailure.STORAGE_ERROR);
            return Result.FAILED;
        }
    }

    /** 서버가 08시 이후 재시작하면 이미 지난 슬롯 중 마지막 슬롯만 보완한다. */
    public Result runLatestDueSlot() {
        int slot = latestDueSlot(now().toLocalTime());
        return slot == 0 ? Result.SKIPPED : runSlot(slot);
    }

    private Long ensureGeneration(LocalDate date) {
        LocalDateTime key = date.atTime(8, 0);
        try {
            return transactions.execute(() -> generations.findByGenerationDate(key)
                    .orElseGet(() -> generations.saveAndFlush(TrendGeneration.create(key, TrendGenerationStatus.PENDING, 0, null)))
                    .getTrendGenerationId());
        } catch (DataIntegrityViolationException duplicate) {
            // UNIQUE 경쟁은 실패한 트랜잭션이 끝난 뒤 승자가 만든 행으로 합류한다.
            return generations.findByGenerationDate(key).orElseThrow(() -> duplicate).getTrendGenerationId();
        }
    }

    private void recordFailure(Claim claim, TrendGenerationFailure failure) {
        transactions.execute(() -> {
            var generation = generations.findByIdForUpdate(claim.id()).orElseThrow();
            if (generation.getStatus() == TrendGenerationStatus.PROCESSING
                    && generation.getAttemptCount() == claim.attempt()) generation.failAttempt(failure.name());
            return null;
        });
        log.warn("경제 트렌드 생성 실패. generationId={}, attempt={}, errorType={}", claim.id(), claim.attempt(), failure);
    }

    private boolean isRetryable(String error) {
        if (error == null) return false;
        try { return TrendGenerationFailure.valueOf(error).isRetryable(); }
        catch (IllegalArgumentException ignored) { return false; }
    }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(KST)); }
    private int latestDueSlot(LocalTime time) {
        if (!time.isBefore(LocalTime.of(8, 30))) return 4;
        if (!time.isBefore(LocalTime.of(8, 15))) return 3;
        if (!time.isBefore(LocalTime.of(8, 5))) return 2;
        if (!time.isBefore(LocalTime.of(8, 0))) return 1;
        return 0;
    }
}
