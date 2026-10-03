package demoday.backend.ranking.repository;

import demoday.backend.ranking.code.RankingType;
import demoday.backend.ranking.domain.RankingReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface RankingRewardRepository extends JpaRepository<RankingReward, Long> {

    boolean existsByMemberMemberIdAndRankingTypeAndRankingDate(
            Long memberId,
            RankingType rankingType,
            LocalDate rankingDate
    );
}
