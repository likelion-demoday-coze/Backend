package demoday.backend.trend.service;

import demoday.backend.trend.code.TrendGenerationStatus;
import demoday.backend.trend.dto.RecentTrendContent;
import demoday.backend.trend.repository.EconomicTrendRepository;
import demoday.backend.trend.repository.TrendReferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecentTrendContentService {
    private final EconomicTrendRepository trends;
    private final TrendReferenceRepository references;

    /** 운영 생성은 직전 3일, 로컬 비교는 오늘을 포함한 3일의 성공 콘텐츠만 참고한다. */
    public List<RecentTrendContent> getRecent(LocalDate date, boolean includeToday) {
        LocalDate from = date.minusDays(includeToday ? 2 : 3);
        LocalDate to = includeToday ? date.plusDays(1) : date;
        return trends.findRecentContent(TrendGenerationStatus.SUCCESS,
                        from.atStartOfDay(), to.atStartOfDay(), PageRequest.of(0, 9)).stream()
                .map(trend -> new RecentTrendContent(trend.getTrendGeneration().getGenerationDate().toLocalDate(),
                        trend.getTitle(), shorten(trend.getSummary()),
                        references.findAllByEconomicTrendEconomicTrendIdOrderByTrendReferenceIdAsc(trend.getEconomicTrendId())
                                .stream().limit(3).map(reference -> reference.getUrl()).toList()))
                .toList();
    }

    private String shorten(String summary) {
        int length = summary.codePointCount(0, summary.length());
        return length <= 1000 ? summary : summary.substring(0, summary.offsetByCodePoints(0, 1000));
    }
}
