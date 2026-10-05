package demoday.backend.trend.service;

import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.trend.code.TrendGenerationStatus;
import demoday.backend.trend.domain.*;
import demoday.backend.trend.dto.GeneratedTrendContent;
import demoday.backend.trend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TrendContentStorageService {
    private final TrendGenerationRepository generations;
    private final EconomicTrendRepository trends;
    private final EconomicTermRepository terms;
    private final TrendReferenceRepository references;
    private final TrendContentValidator validator;
    private final TransactionRetryExecutor transactions;

    /** 외부 호출 없이 콘텐츠 전체와 SUCCESS를 같은 트랜잭션에서 확정한다. */
    public void saveValidated(Long generationId, GeneratedTrendContent content) {
        transactions.execute(() -> {
            var generation = generations.findByIdForUpdate(generationId).orElseThrow();
            if (generation.getStatus() == TrendGenerationStatus.SUCCESS) return null;
            if (generation.getStatus() != TrendGenerationStatus.PROCESSING)
                throw new IllegalStateException("생성 중인 작업만 저장할 수 있습니다.");
            validator.validate(content, generation.getGenerationDate().toLocalDate());
            int order = 1;
            for (var item : content.items()) {
                var trend = trends.save(EconomicTrend.create(generation, order++, item.title(), item.summary()));
                for (var term : item.terms()) terms.save(EconomicTerm.create(trend, term.name(), term.description()));
                for (var reference : item.references()) references.save(TrendReference.create(trend,
                        reference.title(), reference.url(), reference.publisher(), reference.publishedDate()));
            }
            generation.completeSuccessfully();
            return null;
        });
    }
}
