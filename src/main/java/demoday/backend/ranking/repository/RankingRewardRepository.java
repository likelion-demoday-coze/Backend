package demoday.backend.ranking.repository;

import demoday.backend.ranking.code.RankingType;
import demoday.backend.ranking.domain.RankingReward;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RankingRewardRepository extends JpaRepository<RankingReward, Long> {

    boolean existsByMemberMemberIdAndRankingTypeAndRankingDate(
            Long memberId,
            RankingType rankingType,
            LocalDate rankingDate
    );

    List<RankingReward>
    findAllByMemberMemberIdAndRankingDateAndReadAtIsNullOrderByRankingRewardIdAsc(
            Long memberId,
            LocalDate rankingDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r
        FROM RankingReward r
        WHERE r.rankingRewardId = :rewardId
          AND r.member.memberId = :memberId
        """)
    Optional<RankingReward> findByIdAndMemberIdForUpdate(
            @Param("rewardId") Long rewardId,
            @Param("memberId") Long memberId
    );
}
