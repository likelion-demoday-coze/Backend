package demoday.backend.ranking.repository;

import demoday.backend.ranking.domain.RankingSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RankingSettlementRepository
        extends JpaRepository<RankingSettlement, Long> {

    boolean existsByRankingDate(LocalDate rankingDate);

    List<RankingSettlement>
    findAllByRankingDateBetweenOrderByRankingDateAsc(
            LocalDate from,
            LocalDate to
    );
}
