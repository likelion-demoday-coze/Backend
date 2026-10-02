package demoday.backend.ranking.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.ranking.code.RankingErrorCode;
import demoday.backend.ranking.dto.*;
import demoday.backend.ranking.repository.RankingQueryRepository;
import demoday.backend.ranking.repository.projection.StockRankingRow;
import demoday.backend.ranking.repository.projection.StockRankingStatsRow;
import demoday.backend.ranking.repository.projection.TimeAttackRankingRow;
import demoday.backend.ranking.repository.projection.TimeAttackRankingStatsRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static demoday.backend.ranking.code.RankingPolicy.KST;
import static demoday.backend.ranking.code.RankingPolicy.MAX_PAGE_SIZE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RankingQueryService {

    private final RankingQueryRepository rankingQueryRepository;
    private final MemberRepository memberRepository;
    private final Clock clock;

    public StockRankingResponse getStockRanking(
            Long memberId,
            int page,
            int size
    ) {
        // 페이지 요청값, 로그인 회원 검증
        validateRequest(memberId, page, size);

        // 조회 위치 계산
        long offset = calculateOffset(page, size);

        // 랭킹 목록 조회
        List<StockRankingRow> fetchedRows =
                rankingQueryRepository
                        .findStockRankingPage(
                                size+1,
                                offset
                        );

        boolean hasNext =
                fetchedRows.size() > size;

        List<StockRankingEntryResponse> rankings =
                fetchedRows.stream()
                        .limit(size)
                        .map(
                                StockRankingEntryResponse::from
                        )
                        .toList();

        // 내 주가 순위 별도 조회
        StockRankingRow myRankingRow =
                rankingQueryRepository
                        .findMyStockRanking(memberId)
                        .orElseThrow(
                                () -> new ProjectException(
                                        GeneralErrorCode.NOT_FOUND
                                )
                        );

        // 전체 통계 조회
        StockRankingStatsRow stats =
                rankingQueryRepository
                        .findStockRankingStats();

        // 백분율 계산
        MyStockRankingResponse myRanking =
                MyStockRankingResponse.of(
                        myRankingRow,
                        calculateTopPercent(
                                myRankingRow
                                        .getRankingPosition(),
                                stats.getTotalMemberCount()
                        )
                );

        LocalDateTime rankedAt =
                LocalDateTime.now(
                                clock.withZone(KST)
                        )
                        .truncatedTo(
                                ChronoUnit.SECONDS
                        );

        // 조회 시각, 목록, 내 순위, 페이지 정보 리턴
        return new StockRankingResponse(
                rankedAt,
                stats.getTotalMemberCount(),
                stats.getAverageStock(),
                rankings,
                myRanking,
                new RankingPageResponse(
                        page,
                        size,
                        hasNext
                )
        );
    }

    public TimeAttackRankingResponse getTimeAttackRanking(
            Long memberId,
            int page,
            int size
    ) {

        // 페이지 요청값, 로그인 회원 검증
        validateRequest(
                memberId,
                page,
                size
        );

        LocalDate rankingDate =
                LocalDate.now(
                        clock.withZone(KST)
                );

        long offset = calculateOffset(
                page,
                size
        );

        // 오늘 완료된 시간외거래 세션만 조회
        List<TimeAttackRankingRow> fetchedRows =
                rankingQueryRepository
                        .findTimeAttackRankingPage(
                                rankingDate,
                                size + 1,
                                offset
                        );

        boolean hasNext =
                fetchedRows.size() > size;

        // 오늘 최고 기록과 순위 별도 조회
        List<TimeAttackRankingEntryResponse> rankings =
                fetchedRows.stream()
                        .limit(size)
                        .map(
                                TimeAttackRankingEntryResponse::from
                        )
                        .toList();

        TimeAttackRankingStatsRow stats =
                rankingQueryRepository
                        .findTimeAttackRankingStats(
                                rankingDate
                        );

        // 참여 회원 수와 평균 정답 수 조회
        MyTimeAttackRankingResponse myRanking =
                rankingQueryRepository
                        .findMyTimeAttackRanking(
                                memberId,
                                rankingDate
                        )
                        .map(row ->
                                MyTimeAttackRankingResponse.of(
                                        row,
                                        calculateTopPercent(
                                                row.getRankingPosition(),
                                                stats.getTotalMemberCount()
                                        )
                                )
                        )
                        .orElse(null);

        // 날짜, 목록, 내 순위, 통계, 페이지 정보 리턴
        return new TimeAttackRankingResponse(
                rankingDate,
                stats.getTotalMemberCount(),
                stats.getAverageCorrectCount(),
                rankings,
                myRanking,
                new RankingPageResponse(
                        page,
                        size,
                        hasNext
                )
        );
    }

    private void validateRequest(Long memberId, int page, int size) {
        validatePage(page, size);

        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(
                        GeneralErrorCode.NOT_FOUND
                ));

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(
                    GeneralErrorCode.FORBIDDEN
            );
        }
    }

    private void validatePage(int page, int size) {
        if (page < 0
        || size < 1
        || size > MAX_PAGE_SIZE) {
            throw new ProjectException(
                    RankingErrorCode.INVALID_PAGE
            );
        }
    }

    private long calculateOffset(int page, int size) {
        return (long) page * size;
    }

    private BigDecimal calculateTopPercent(
            long rankingPosition,
            long totalMemberCount
    ) {
        if (totalMemberCount <= 0) {
            return BigDecimal.ZERO
                    .setScale(1);
        }

        return BigDecimal
                .valueOf(rankingPosition)
                .multiply(
                        BigDecimal.valueOf(100)
                )
                .divide(
                        BigDecimal.valueOf(
                                totalMemberCount
                        ),
                        1,
                        RoundingMode.HALF_UP
                );
    }
}
