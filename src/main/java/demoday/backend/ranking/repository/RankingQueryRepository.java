package demoday.backend.ranking.repository;

import demoday.backend.member.domain.Member;
import demoday.backend.ranking.repository.projection.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@SuppressWarnings({
        "SqlNoDataSourceInspection",
        "SqlResolve"
})
public interface RankingQueryRepository
        extends Repository<Member, Long> {

    @Query(
            value = /* language=MySQL */ """
                    WITH ranked AS (
                        SELECT
                            m.member_id AS memberId,
                            m.nickname AS nickname,
                            m.current_stock AS currentStock,
                            RANK() OVER (
                                ORDER BY m.current_stock DESC
                            ) AS rankingPosition
                        FROM member m
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        currentStock,
                        rankingPosition
                    FROM ranked
                    ORDER BY
                        rankingPosition ASC,
                        memberId ASC
                    LIMIT :fetchSize
                    OFFSET :offset
                    """,
            nativeQuery = true
    )
    List<StockRankingRow> findStockRankingPage(
            @Param("fetchSize") int fetchSize,
            @Param("offset") long offset
    );

    @Query(
            value = /* language=MySQL */ """
                    WITH ranked AS (
                        SELECT
                            m.member_id AS memberId,
                            m.nickname AS nickname,
                            m.current_stock AS currentStock,
                            RANK() OVER (
                                ORDER BY m.current_stock DESC
                            ) AS rankingPosition
                        FROM member m
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        currentStock,
                        rankingPosition
                    FROM ranked
                    WHERE memberId = :memberId
                    """,
            nativeQuery = true
    )
    Optional<StockRankingRow> findMyStockRanking(
            @Param("memberId") Long memberId
    );

    @Query(
            value = /* language=MySQL */ """
                    SELECT
                        COUNT(*) AS totalMemberCount,
                        CAST(
                            COALESCE(
                                AVG(m.current_stock),
                                0
                            )
                            AS DECIMAL(30, 2)
                        ) AS averageStock
                    FROM member m
                    WHERE m.status = 'ACTIVE'
                    """,
            nativeQuery = true
    )
    StockRankingStatsRow findStockRankingStats();

    @Query(
            value = /* language=MySQL */ """
                    WITH member_best AS (
                        SELECT
                            s.member_id AS memberId,
                            MAX(s.correct_count) AS correctCount
                        FROM time_attack_session s
                        WHERE s.attempt_date = :rankingDate
                          AND s.status = 'COMPLETED'
                        GROUP BY s.member_id
                    ),
                    ranked AS (
                        SELECT
                            m.member_id AS memberId,
                            m.nickname AS nickname,
                            b.correctCount AS correctCount,
                            RANK() OVER (
                                ORDER BY b.correctCount DESC
                            ) AS rankingPosition
                        FROM member_best b
                        JOIN member m
                          ON m.member_id = b.memberId
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        correctCount,
                        rankingPosition
                    FROM ranked
                    ORDER BY
                        rankingPosition ASC,
                        memberId ASC
                    LIMIT :fetchSize
                    OFFSET :offset
                    """,
            nativeQuery = true
    )
    List<TimeAttackRankingRow>
    findTimeAttackRankingPage(
            @Param("rankingDate")
            LocalDate rankingDate,

            @Param("fetchSize")
            int fetchSize,

            @Param("offset")
            long offset
    );

    @Query(
            value = /* language=MySQL */ """
                    WITH member_best AS (
                        SELECT
                            s.member_id AS memberId,
                            MAX(s.correct_count) AS correctCount
                        FROM time_attack_session s
                        WHERE s.attempt_date = :rankingDate
                          AND s.status = 'COMPLETED'
                        GROUP BY s.member_id
                    ),
                    ranked AS (
                        SELECT
                            m.member_id AS memberId,
                            m.nickname AS nickname,
                            b.correctCount AS correctCount,
                            RANK() OVER (
                                ORDER BY b.correctCount DESC
                            ) AS rankingPosition
                        FROM member_best b
                        JOIN member m
                          ON m.member_id = b.memberId
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        correctCount,
                        rankingPosition
                    FROM ranked
                    WHERE memberId = :memberId
                    """,
            nativeQuery = true
    )
    Optional<TimeAttackRankingRow>
    findMyTimeAttackRanking(
            @Param("memberId")
            Long memberId,

            @Param("rankingDate")
            LocalDate rankingDate
    );

    @Query(
            value = /* language=MySQL */ """
                    WITH member_best AS (
                        SELECT
                            s.member_id AS memberId,
                            MAX(s.correct_count) AS correctCount
                        FROM time_attack_session s
                        WHERE s.attempt_date = :rankingDate
                          AND s.status = 'COMPLETED'
                        GROUP BY s.member_id
                    )
                    SELECT
                        COUNT(*) AS totalMemberCount,
                        CAST(
                            COALESCE(
                                AVG(b.correctCount),
                                0
                            )
                            AS DECIMAL(10, 2)
                        ) AS averageCorrectCount
                    FROM member_best b
                    JOIN member m
                      ON m.member_id = b.memberId
                    WHERE m.status = 'ACTIVE'
                    """,
            nativeQuery = true
    )
    TimeAttackRankingStatsRow
    findTimeAttackRankingStats(
            @Param("rankingDate")
            LocalDate rankingDate
    );

    @Query(
            value = /* language=MySQL */ """
                WITH ranked AS (
                    SELECT
                        s.member_id AS memberId,
                        RANK() OVER (
                            ORDER BY s.stock_value DESC
                        ) AS rankingPosition
                    FROM stock_daily_snapshot s
                    JOIN member m
                      ON m.member_id = s.member_id
                    WHERE s.snapshot_date = :rankingDate
                      AND m.status = 'ACTIVE'
                )
                SELECT
                    memberId,
                    rankingPosition
                FROM ranked
                WHERE rankingPosition BETWEEN 1 AND :maxRank
                ORDER BY
                    rankingPosition ASC,
                    memberId ASC
                """,
            nativeQuery = true
    )
    List<RankingWinnerRow> findStockRewardTargets(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("maxRank") int maxRank
    );

    @Query(
            value = /* language=MySQL */ """
                WITH member_best AS (
                    SELECT
                        s.member_id AS memberId,
                        MAX(s.correct_count) AS correctCount
                    FROM time_attack_session s
                    WHERE s.attempt_date = :rankingDate
                      AND s.status = 'COMPLETED'
                    GROUP BY s.member_id
                ),
                ranked AS (
                    SELECT
                        m.member_id AS memberId,
                        RANK() OVER (
                            ORDER BY b.correctCount DESC
                        ) AS rankingPosition
                    FROM member_best b
                    JOIN member m
                      ON m.member_id = b.memberId
                    WHERE m.status = 'ACTIVE'
                )
                SELECT
                    memberId,
                    rankingPosition
                FROM ranked
                WHERE rankingPosition BETWEEN 1 AND :maxRank
                ORDER BY
                    rankingPosition ASC,
                    memberId ASC
                """,
            nativeQuery = true
    )
    List<RankingWinnerRow> findTimeAttackRewardTargets(
            @Param("rankingDate")
            LocalDate rankingDate,

            @Param("maxRank")
            int maxRank
    );
}
