package demoday.backend.ranking.repository;

import demoday.backend.member.domain.Member;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingQueryRepository
        extends Repository<Member, Long> {

    //noinspection SqlNoDataSourceInspection,SqlResolve
    @Query(
            value = /* language=MySQL */ """
                    WITH ranked AS (
                        SELECT
                            m.member_id AS memberId,
                            m.nickname AS nickname,
                            m.current_stock AS currentStock,
                            RANK() OVER (
                                ORDER BY m.current_stock DESC
                            ) AS rankingPosition,
                            COUNT(*) OVER () AS totalMemberCount,
                            CAST(
                                AVG(m.current_stock) OVER ()
                                AS DECIMAL(30, 2)
                            ) AS averageStock
                        FROM member m
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        currentStock,
                        rankingPosition,
                        totalMemberCount,
                        averageStock
                    FROM ranked
                    WHERE rankingPosition <= :rankingLimit
                    ORDER BY
                        rankingPosition ASC,
                        memberId ASC
                    """,
            nativeQuery = true
    )
    List<StockRankingRow> findTopStockRankings(
            @Param("rankingLimit") int rankingLimit
    );

    //noinspection SqlNoDataSourceInspection,SqlResolve
    @Query(
            value = /* language=MySQL */ """
                    WITH ranked AS (
                        SELECT
                            m.member_id AS memberId,
                            m.nickname AS nickname,
                            m.current_stock AS currentStock,
                            RANK() OVER (
                                ORDER BY m.current_stock DESC
                            ) AS rankingPosition,
                            COUNT(*) OVER () AS totalMemberCount,
                            CAST(
                                AVG(m.current_stock) OVER ()
                                AS DECIMAL(30, 2)
                            ) AS averageStock
                        FROM member m
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        currentStock,
                        rankingPosition,
                        totalMemberCount,
                        averageStock
                    FROM ranked
                    WHERE memberId = :memberId
                    """,
            nativeQuery = true
    )
    Optional<StockRankingRow> findMyStockRanking(
            @Param("memberId") Long memberId
    );

    //noinspection SqlNoDataSourceInspection,SqlResolve
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
                            ) AS rankingPosition,
                            COUNT(*) OVER () AS totalMemberCount,
                            CAST(
                                AVG(b.correctCount) OVER ()
                                AS DECIMAL(10, 2)
                            ) AS averageCorrectCount
                        FROM member_best b
                        JOIN member m
                          ON m.member_id = b.memberId
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        correctCount,
                        rankingPosition,
                        totalMemberCount,
                        averageCorrectCount
                    FROM ranked
                    WHERE rankingPosition <= :rankingLimit
                    ORDER BY
                        rankingPosition ASC,
                        memberId ASC
                    """,
            nativeQuery = true
    )
    List<TimeAttackRankingRow> findTopTimeAttackRankings(
            @Param("rankingDate") LocalDate rankingDate,
            @Param("rankingLimit") int rankingLimit
    );

    //noinspection SqlNoDataSourceInspection,SqlResolve
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
                            ) AS rankingPosition,
                            COUNT(*) OVER () AS totalMemberCount,
                            CAST(
                                AVG(b.correctCount) OVER ()
                                AS DECIMAL(10, 2)
                            ) AS averageCorrectCount
                        FROM member_best b
                        JOIN member m
                          ON m.member_id = b.memberId
                        WHERE m.status = 'ACTIVE'
                    )
                    SELECT
                        memberId,
                        nickname,
                        correctCount,
                        rankingPosition,
                        totalMemberCount,
                        averageCorrectCount
                    FROM ranked
                    WHERE memberId = :memberId
                    """,
            nativeQuery = true
    )
    Optional<TimeAttackRankingRow> findMyTimeAttackRanking(
            @Param("memberId") Long memberId,
            @Param("rankingDate") LocalDate rankingDate
    );
}
