package demoday.backend.trend.service;

import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.trend.code.TrendGenerationStatus;
import demoday.backend.trend.domain.*;
import demoday.backend.trend.dto.GeneratedTrendContent;
import demoday.backend.trend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class TrendContentStorageService {
    private final TrendGenerationRepository generations;
    private final EconomicTrendRepository trends;
    private final EconomicTermRepository terms;
    private final TrendReferenceRepository references;
    private final TrendContentValidator validator;
    private final TransactionRetryExecutor transactions;
    private final Clock clock;

    /** 외부 호출 없이 콘텐츠 전체와 SUCCESS를 같은 트랜잭션에서 확정한다. */
    public void saveValidated(Long generationId, GeneratedTrendContent content) {
        save(generationId, null, content);
    }

    /** 실행 기한이 지났거나 다른 시도에 넘겨진 응답은 저장하지 않는다. */
    public boolean saveForAttempt(Long generationId, int attempt, GeneratedTrendContent content) {
        return save(generationId, attempt, content);
    }

    private boolean save(Long generationId, Integer attempt, GeneratedTrendContent content) {
        return transactions.execute(() -> {
            var generation = generations.findByIdForUpdate(generationId).orElseThrow();
            if (attempt != null && !generation.ownsAttempt(attempt, LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))))) return false;
            if (generation.getStatus() == TrendGenerationStatus.SUCCESS) return false;
            if (generation.getStatus() != TrendGenerationStatus.PROCESSING)
                throw new IllegalStateException("생성 중인 작업만 저장할 수 있습니다.");
            validator.validate(content, generation.getGenerationDate().toLocalDate());
            int order = 1;
            for (var item : content.items()) {
                var trend = trends.save(EconomicTrend.create(generation, order++, item.title(), item.summary(), item.category()));
                for (var term : item.terms()) terms.save(EconomicTerm.create(trend, term.name(), term.description()));
                for (var reference : item.references()) references.save(TrendReference.create(trend,
                        reference.title(), reference.url(), reference.publisher(), reference.publishedDate()));
            }
            generation.completeSuccessfully();
            return true;
        });
    }
}
