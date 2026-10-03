package demoday.backend.ranking.repository;

import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.ranking.repository.projection.RankingWinnerRow;
import demoday.backend.ranking.repository.projection.StockRankingRow;
import demoday.backend.ranking.repository.projection.StockRankingStatsRow;
import demoday.backend.ranking.repository.projection.TimeAttackRankingRow;
import demoday.backend.ranking.repository.projection.TimeAttackRankingStatsRow;
import demoday.backend.stock.domain.StockDailySnapshot;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import demoday.backend.timeattack.domain.TimeAttackSession;
import demoday.backend.timeattack.repository.TimeAttackSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.open-in-view=false"
})
@Transactional
class RankingQueryRepositoryMySqlTest {

    private static final LocalDate RANKING_DATE = LocalDate.of(2026, 10, 2);

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.36");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver"
        );
    }

    @Autowired RankingQueryRepository rankingQueryRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired TimeAttackSessionRepository timeAttackSessionRepository;
    @Autowired StockDailySnapshotRepository stockDailySnapshotRepository;

    private Member first;
    private Member second;
    private Member third;
    private Member fourth;

    @BeforeEach
    void setUp() {
        first = saveMember(1L, "일등", "400.00");
        second = saveMember(2L, "이등", "300.00");
        third = saveMember(3L, "공동이등", "300.00");
        fourth = saveMember(4L, "사등", "100.00");
    }

    @Test
    @DisplayName("주가 동점자는 같은 순위이며 다음 순위는 건너뛴다")
    void stockRankingUsesCompetitionRanking() {
        List<StockRankingRow> rows =
                rankingQueryRepository.findStockRankingPage(10, 0);

        assertThat(rows)
                .extracting(
                        StockRankingRow::getMemberId,
                        StockRankingRow::getRankingPosition
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                first.getMemberId(), 1L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                second.getMemberId(), 2L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                third.getMemberId(), 2L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                fourth.getMemberId(), 4L
                        )
                );

        assertThat(rankingQueryRepository
                .findMyStockRanking(third.getMemberId()))
                .get()
                .extracting(StockRankingRow::getRankingPosition)
                .isEqualTo(2L);

        StockRankingStatsRow stats =
                rankingQueryRepository.findStockRankingStats();
        assertThat(stats.getTotalMemberCount()).isEqualTo(4L);
        assertThat(stats.getAverageStock()).isEqualByComparingTo("275.00");
    }

    @Test
    @DisplayName("주가 랭킹은 offset과 fetchSize를 적용한다")
    void stockRankingSupportsSlicePagination() {
        List<StockRankingRow> rows =
                rankingQueryRepository.findStockRankingPage(2, 2);

        assertThat(rows)
                .extracting(StockRankingRow::getMemberId)
                .containsExactly(third.getMemberId(), fourth.getMemberId());
    }

    @Test
    @DisplayName("주가 보상 대상은 현재 주가가 아닌 정산일 스냅샷으로 결정한다")
    void findsStockRewardTargets() {
        saveSnapshot(first, RANKING_DATE, "100.00");
        saveSnapshot(second, RANKING_DATE, "500.00");
        saveSnapshot(third, RANKING_DATE, "500.00");
        saveSnapshot(fourth, RANKING_DATE, "200.00");

        List<RankingWinnerRow> winners =
                rankingQueryRepository.findStockRewardTargets(
                        RANKING_DATE, 3
                );

        assertThat(winners)
                .extracting(
                        RankingWinnerRow::getMemberId,
                        RankingWinnerRow::getRankingPosition
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                second.getMemberId(), 1L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                third.getMemberId(), 1L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                fourth.getMemberId(), 3L
                        )
                );
    }

    @Test
    @DisplayName("다른 날짜의 주가 스냅샷은 보상 순위에 포함하지 않는다")
    void stockRewardTargetsUseRequestedSnapshotDateOnly() {
        saveSnapshot(first, RANKING_DATE.minusDays(1), "999.00");
        saveSnapshot(second, RANKING_DATE, "300.00");
        saveSnapshot(third, RANKING_DATE, "200.00");

        List<RankingWinnerRow> winners =
                rankingQueryRepository.findStockRewardTargets(
                        RANKING_DATE, 3
                );

        assertThat(winners)
                .extracting(RankingWinnerRow::getMemberId)
                .containsExactly(second.getMemberId(), third.getMemberId());
    }

    @Test
    @DisplayName("타임어택은 당일 완료 세션 중 회원별 최고 정답 수로 순위를 계산한다")
    void timeAttackRankingUsesDailyMemberBest() {
        saveCompletedSession(first, RANKING_DATE, 10);
        saveCompletedSession(first, RANKING_DATE, 15);
        saveCompletedSession(second, RANKING_DATE, 12);
        saveCompletedSession(third, RANKING_DATE, 12);
        saveCompletedSession(fourth, RANKING_DATE.minusDays(1), 100);
        saveInProgressSession(fourth, RANKING_DATE, 100);

        List<TimeAttackRankingRow> rows =
                rankingQueryRepository.findTimeAttackRankingPage(
                        RANKING_DATE, 10, 0
                );

        assertThat(rows)
                .extracting(
                        TimeAttackRankingRow::getMemberId,
                        TimeAttackRankingRow::getCorrectCount,
                        TimeAttackRankingRow::getRankingPosition
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                first.getMemberId(), 15, 1L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                second.getMemberId(), 12, 2L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                third.getMemberId(), 12, 2L
                        )
                );

        TimeAttackRankingRow mine = rankingQueryRepository
                .findMyTimeAttackRanking(second.getMemberId(), RANKING_DATE)
                .orElseThrow();
        assertThat(mine.getCorrectCount()).isEqualTo(12);
        assertThat(mine.getRankingPosition()).isEqualTo(2L);

        assertThat(rankingQueryRepository.findMyTimeAttackRanking(
                fourth.getMemberId(), RANKING_DATE
        )).isEmpty();
    }

    @Test
    @DisplayName("타임어택 통계와 보상 대상도 회원별 최고 완료 기록을 사용한다")
    void timeAttackStatsAndRewardTargetsUseDailyMemberBest() {
        saveCompletedSession(first, RANKING_DATE, 15);
        saveCompletedSession(first, RANKING_DATE, 5);
        saveCompletedSession(second, RANKING_DATE, 12);
        saveCompletedSession(third, RANKING_DATE, 12);
        saveCompletedSession(fourth, RANKING_DATE, 1);

        TimeAttackRankingStatsRow stats =
                rankingQueryRepository.findTimeAttackRankingStats(RANKING_DATE);
        assertThat(stats.getTotalMemberCount()).isEqualTo(4L);
        assertThat(stats.getAverageCorrectCount()).isEqualByComparingTo("10.00");

        List<RankingWinnerRow> winners =
                rankingQueryRepository.findTimeAttackRewardTargets(
                        RANKING_DATE, 3
                );

        assertThat(winners)
                .extracting(
                        RankingWinnerRow::getMemberId,
                        RankingWinnerRow::getRankingPosition
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                first.getMemberId(), 1L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                second.getMemberId(), 2L
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                third.getMemberId(), 2L
                        )
                );
    }

    private Member saveMember(
            Long kakaoUserId,
            String nickname,
            String stock
    ) {
        Member member = Member.create(kakaoUserId, nickname);
        member.changeStock(new BigDecimal(stock));
        return memberRepository.save(member);
    }

    private void saveSnapshot(
            Member member,
            LocalDate date,
            String stock
    ) {
        stockDailySnapshotRepository.save(
                StockDailySnapshot.create(
                        member,
                        date,
                        new BigDecimal(stock)
                )
        );
    }

    private void saveCompletedSession(
            Member member,
            LocalDate date,
            int correctCount
    ) {
        LocalDateTime startedAt = date.atTime(12, 0);
        TimeAttackSession session = TimeAttackSession.create(
                member,
                date,
                startedAt,
                false
        );
        for (int count = 0; count < correctCount; count++) {
            session.increaseCorrectCount();
        }
        session.complete(startedAt.plusSeconds(60));
        timeAttackSessionRepository.save(session);
    }

    private void saveInProgressSession(
            Member member,
            LocalDate date,
            int correctCount
    ) {
        TimeAttackSession session = TimeAttackSession.create(
                member,
                date,
                date.atTime(12, 0),
                false
        );
        for (int count = 0; count < correctCount; count++) {
            session.increaseCorrectCount();
        }
        timeAttackSessionRepository.save(session);
    }
}
