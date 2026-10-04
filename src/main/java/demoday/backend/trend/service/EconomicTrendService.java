package demoday.backend.trend.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.trend.code.*;
import demoday.backend.trend.domain.*;
import demoday.backend.trend.dto.*;
import demoday.backend.trend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EconomicTrendService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MemberRepository members;
    private final TrendGenerationRepository generations;
    private final EconomicTrendRepository trends;
    private final Clock clock;

    /** 외부 API 호출 없이, 최근 72시간의 완성된 성공 콘텐츠만 조회한다. */
    public TodayTrendsResponse getToday(Long memberId) {
        if (memberId == null) throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        Member member = members.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new ProjectException(TrendErrorCode.INACTIVE_MEMBER);
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));
        for (TrendGeneration generation : generations.findAllByStatusAndGenerationDateBetweenOrderByGenerationDateDesc(
                TrendGenerationStatus.SUCCESS, now.minusHours(72), now)) {
            List<EconomicTrend> items = trends.findAllByTrendGenerationTrendGenerationIdOrderByDisplayOrderAsc(
                    generation.getTrendGenerationId());
            if (items.size() != 3) continue;
            LocalDate contentDate = generation.getGenerationDate().toLocalDate();
            return new TodayTrendsResponse(now.toLocalDate(), contentDate, generation.getGenerationDate(),
                    contentDate.equals(now.toLocalDate()) ? TrendContentStatus.READY : TrendContentStatus.FALLBACK,
                    null, items.stream().map(TrendListItemResponse::from).toList());
        }
        return new TodayTrendsResponse(now.toLocalDate(), null, null, TrendContentStatus.PREPARING,
                "경제 트렌드를 준비하고 있어요", List.of());
    }
}
