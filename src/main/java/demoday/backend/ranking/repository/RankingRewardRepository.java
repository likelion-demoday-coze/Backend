package demoday.backend.ranking.repository;

import demoday.backend.ranking.domain.RankingReward;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingRewardRepository extends JpaRepository<RankingReward, Long> {
}
