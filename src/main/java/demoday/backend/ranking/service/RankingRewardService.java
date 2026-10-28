package demoday.backend.ranking.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.ranking.code.RankingErrorCode;
import demoday.backend.ranking.domain.RankingReward;
import demoday.backend.ranking.dto.UnreadRankingRewardsResponse;
import demoday.backend.ranking.repository.RankingRewardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

import static demoday.backend.ranking.code.RankingPolicy.KST;

@Service
@RequiredArgsConstructor
public class RankingRewardService {

    private final RankingRewardRepository rankingRewardRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public UnreadRankingRewardsResponse getUnreadRewards(
            Long memberId
    ) {
        validateMemberId(memberId);

        return UnreadRankingRewardsResponse.from(
                rankingRewardRepository
                        .findAllByMemberMemberIdAndReadAtIsNullOrderByRankingDateAscRankingRewardIdAsc(
                                memberId
                        )
        );
    }

    @Transactional
    public void markAsRead(
            Long memberId,
            Long rankingRewardId
    ) {
        validateMemberId(memberId);

        if (rankingRewardId == null || rankingRewardId <= 0) {
            throw new ProjectException(
                    RankingErrorCode.REWARD_NOT_FOUND
            );
        }

        RankingReward reward = rankingRewardRepository
                .findByIdAndMemberIdForUpdate(
                        rankingRewardId,
                        memberId
                )
                .orElseThrow(() ->
                        new ProjectException(
                                RankingErrorCode.REWARD_NOT_FOUND
                        )
                );

        reward.markAsRead(
                LocalDateTime.now(clock.withZone(KST))
        );
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }
    }
}
