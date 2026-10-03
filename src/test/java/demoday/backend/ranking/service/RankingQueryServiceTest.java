package demoday.backend.ranking.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.ranking.code.RankingErrorCode;
import demoday.backend.ranking.dto.StockRankingResponse;
import demoday.backend.ranking.dto.TimeAttackRankingResponse;
import demoday.backend.ranking.repository.RankingQueryRepository;
import demoday.backend.ranking.repository.projection.StockRankingRow;
import demoday.backend.ranking.repository.projection.StockRankingStatsRow;
import demoday.backend.ranking.repository.projection.TimeAttackRankingRow;
import demoday.backend.ranking.repository.projection.TimeAttackRankingStatsRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingQueryServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long MEMBER_ID = 1L;
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-03T06:30:45Z"),
            KST
    );

    @Mock RankingQueryRepository rankingQueryRepository;
    @Mock MemberRepository memberRepository;

    private RankingQueryService rankingQueryService;

    @BeforeEach
    void setUp() {
        rankingQueryService = new RankingQueryService(
                rankingQueryRepository,
                memberRepository,
                CLOCK
        );
    }

    @Test
    @DisplayName("주가 랭킹은 size+1개를 조회해 다음 페이지 여부와 내 상위 비율을 계산한다")
    void getStockRanking() {
        allowActiveMember();

        StockRankingRow first = stockRow(2L, "둘", "300.00", 1L);
        StockRankingRow mine = stockRow(MEMBER_ID, "나", "200.00", 2L);
        StockRankingRow next = stockRow(3L, "셋", "100.00", 3L);
        StockRankingStatsRow stats = mock(StockRankingStatsRow.class);

        when(rankingQueryRepository.findStockRankingPage(3, 0L))
                .thenReturn(List.of(first, mine, next));
        when(rankingQueryRepository.findMyStockRanking(MEMBER_ID))
                .thenReturn(Optional.of(mine));
        when(rankingQueryRepository.findStockRankingStats()).thenReturn(stats);
        when(stats.getTotalMemberCount()).thenReturn(4L);
        when(stats.getAverageStock()).thenReturn(new BigDecimal("175.00"));

        StockRankingResponse result = rankingQueryService.getStockRanking(
                MEMBER_ID,
                0,
                2
        );

        assertThat(result.rankedAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 3, 15, 30, 45));
        assertThat(result.totalMemberCount()).isEqualTo(4);
        assertThat(result.averageStock()).isEqualByComparingTo("175.00");
        assertThat(result.rankings()).hasSize(2);
        assertThat(result.rankings().get(0).rankingPosition()).isEqualTo(1);
        assertThat(result.myRanking().rankingPosition()).isEqualTo(2);
        assertThat(result.myRanking().topPercent()).isEqualByComparingTo("50.0");
        assertThat(result.page().hasNext()).isTrue();
        assertThat(result.page().page()).isZero();
        assertThat(result.page().size()).isEqualTo(2);
    }

    @Test
    @DisplayName("주가 랭킹은 페이지 번호에 맞는 offset으로 조회한다")
    void getStockRankingUsesOffset() {
        allowActiveMember();
        StockRankingRow mine = stockRow(MEMBER_ID, "나", "100.00", 1L);
        StockRankingStatsRow stats = mock(StockRankingStatsRow.class);

        when(rankingQueryRepository.findStockRankingPage(21, 40L))
                .thenReturn(List.of());
        when(rankingQueryRepository.findMyStockRanking(MEMBER_ID))
                .thenReturn(Optional.of(mine));
        when(rankingQueryRepository.findStockRankingStats()).thenReturn(stats);
        when(stats.getTotalMemberCount()).thenReturn(1L);
        when(stats.getAverageStock()).thenReturn(new BigDecimal("100.00"));

        StockRankingResponse result = rankingQueryService.getStockRanking(
                MEMBER_ID,
                2,
                20
        );

        assertThat(result.rankings()).isEmpty();
        assertThat(result.page().hasNext()).isFalse();
        verify(rankingQueryRepository).findStockRankingPage(21, 40L);
    }

    @Test
    @DisplayName("오늘 타임어택 랭킹은 회원별 최고 기록과 통계를 반환한다")
    void getTimeAttackRanking() {
        allowActiveMember();
        LocalDate rankingDate = LocalDate.of(2026, 10, 3);
        TimeAttackRankingRow first = timeAttackRow(2L, "둘", 20, 1L);
        TimeAttackRankingRow mine = timeAttackRow(MEMBER_ID, "나", 10, 2L);
        TimeAttackRankingStatsRow stats = mock(TimeAttackRankingStatsRow.class);

        when(rankingQueryRepository.findTimeAttackRankingPage(
                rankingDate, 3, 0L
        )).thenReturn(List.of(first, mine));
        when(rankingQueryRepository.findTimeAttackRankingStats(rankingDate))
                .thenReturn(stats);
        when(stats.getTotalMemberCount()).thenReturn(4L);
        when(stats.getAverageCorrectCount()).thenReturn(new BigDecimal("12.50"));
        when(rankingQueryRepository.findMyTimeAttackRanking(
                MEMBER_ID, rankingDate
        )).thenReturn(Optional.of(mine));

        TimeAttackRankingResponse result =
                rankingQueryService.getTimeAttackRanking(MEMBER_ID, 0, 2);

        assertThat(result.rankingDate()).isEqualTo(rankingDate);
        assertThat(result.rankings()).hasSize(2);
        assertThat(result.averageCorrectCount()).isEqualByComparingTo("12.50");
        assertThat(result.myRanking().correctCount()).isEqualTo(10);
        assertThat(result.myRanking().topPercent()).isEqualByComparingTo("50.0");
        assertThat(result.page().hasNext()).isFalse();
    }

    @Test
    @DisplayName("오늘 타임어택에 참여하지 않은 회원의 내 랭킹은 null이다")
    void getTimeAttackRankingWithoutMyRecord() {
        allowActiveMember();
        LocalDate rankingDate = LocalDate.of(2026, 10, 3);
        TimeAttackRankingStatsRow stats = mock(TimeAttackRankingStatsRow.class);

        when(rankingQueryRepository.findTimeAttackRankingPage(
                rankingDate, 21, 0L
        )).thenReturn(List.of());
        when(rankingQueryRepository.findTimeAttackRankingStats(rankingDate))
                .thenReturn(stats);
        when(stats.getTotalMemberCount()).thenReturn(0L);
        when(stats.getAverageCorrectCount()).thenReturn(BigDecimal.ZERO);
        when(rankingQueryRepository.findMyTimeAttackRanking(
                MEMBER_ID, rankingDate
        )).thenReturn(Optional.empty());

        TimeAttackRankingResponse result =
                rankingQueryService.getTimeAttackRanking(MEMBER_ID, 0, 20);

        assertThat(result.myRanking()).isNull();
        assertThat(result.rankings()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "-1, 20",
            "0, 0",
            "0, 101"
    })
    @DisplayName("잘못된 페이지 요청은 거절한다")
    void rejectInvalidPage(int page, int size) {
        assertError(
                () -> rankingQueryService.getStockRanking(
                        MEMBER_ID, page, size
                ),
                RankingErrorCode.INVALID_PAGE
        );

        verifyNoInteractions(memberRepository, rankingQueryRepository);
    }

    @Test
    @DisplayName("회원 식별자가 없으면 인증 오류를 반환한다")
    void rejectMissingMemberId() {
        assertError(
                () -> rankingQueryService.getStockRanking(null, 0, 20),
                GeneralErrorCode.UNAUTHORIZED
        );
    }

    @Test
    @DisplayName("존재하지 않는 회원의 랭킹 조회를 거절한다")
    void rejectUnknownMember() {
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.empty());

        assertError(
                () -> rankingQueryService.getStockRanking(MEMBER_ID, 0, 20),
                GeneralErrorCode.NOT_FOUND
        );
    }

    @Test
    @DisplayName("비활성 회원의 랭킹 조회를 거절한다")
    void rejectInactiveMember() {
        Member member = mock(Member.class);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.WITHDRAWN);

        assertError(
                () -> rankingQueryService.getStockRanking(MEMBER_ID, 0, 20),
                GeneralErrorCode.FORBIDDEN
        );
    }

    @Test
    @DisplayName("활성 회원의 주가 순위가 조회되지 않으면 리소스 없음 오류를 반환한다")
    void rejectMissingMyStockRanking() {
        allowActiveMember();
        when(rankingQueryRepository.findStockRankingPage(21, 0L))
                .thenReturn(List.of());
        when(rankingQueryRepository.findMyStockRanking(MEMBER_ID))
                .thenReturn(Optional.empty());

        assertError(
                () -> rankingQueryService.getStockRanking(MEMBER_ID, 0, 20),
                GeneralErrorCode.NOT_FOUND
        );
    }

    private void allowActiveMember() {
        Member member = mock(Member.class);
        when(memberRepository.findById(MEMBER_ID)).thenReturn(Optional.of(member));
        when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
    }

    private StockRankingRow stockRow(
            Long memberId,
            String nickname,
            String stock,
            Long position
    ) {
        StockRankingRow row = mock(StockRankingRow.class);
        lenient().when(row.getMemberId()).thenReturn(memberId);
        lenient().when(row.getNickname()).thenReturn(nickname);
        lenient().when(row.getCurrentStock()).thenReturn(new BigDecimal(stock));
        lenient().when(row.getRankingPosition()).thenReturn(position);
        return row;
    }

    private TimeAttackRankingRow timeAttackRow(
            Long memberId,
            String nickname,
            int correctCount,
            Long position
    ) {
        TimeAttackRankingRow row = mock(TimeAttackRankingRow.class);
        lenient().when(row.getMemberId()).thenReturn(memberId);
        lenient().when(row.getNickname()).thenReturn(nickname);
        lenient().when(row.getCorrectCount()).thenReturn(correctCount);
        lenient().when(row.getRankingPosition()).thenReturn(position);
        return row;
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable action,
            Object errorCode
    ) {
        assertThatThrownBy(action)
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(errorCode)
                );
    }
}
