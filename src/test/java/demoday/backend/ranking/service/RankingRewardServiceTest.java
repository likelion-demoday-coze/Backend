package demoday.backend.ranking.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.ranking.code.RankingErrorCode;
import demoday.backend.ranking.code.RankingType;
import demoday.backend.ranking.domain.RankingReward;
import demoday.backend.ranking.dto.UnreadRankingRewardsResponse;
import demoday.backend.ranking.repository.RankingRewardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class RankingRewardServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-28T00:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Mock RankingRewardRepository rankingRewardRepository;

    private RankingRewardService rankingRewardService;

    @BeforeEach
    void setUp() {
        rankingRewardService = new RankingRewardService(
                rankingRewardRepository,
                CLOCK
        );
    }

    @Test
    @DisplayName("미확인 주가와 시간외거래 보상을 팝업 응답 형식으로 반환한다")
    void getUnreadRewards() {
        RankingReward stockReward = reward(
                10L,
                RankingType.STOCK,
                1,
                "1275.40",
                200L,
                300L
        );
        RankingReward timeAttackReward = reward(
                11L,
                RankingType.TIME_ATTACK,
                2,
                "22.00",
                300L,
                400L
        );
        when(rankingRewardRepository
                .findAllByMemberMemberIdAndRankingDateAndReadAtIsNullOrderByRankingRewardIdAsc(
                        1L,
                        LocalDate.of(2026, 10, 27)
                ))
                .thenReturn(List.of(stockReward, timeAttackReward));

        UnreadRankingRewardsResponse response =
                rankingRewardService.getUnreadRewards(1L);

        assertThat(response.rewards()).hasSize(2);
        assertThat(response.rewards().get(0).stockClosingValue())
                .isEqualByComparingTo("1275.40");
        assertThat(response.rewards().get(0).timeAttackCorrectCount())
                .isNull();
        assertThat(response.rewards().get(1).stockClosingValue())
                .isNull();
        assertThat(response.rewards().get(1).timeAttackCorrectCount())
                .isEqualTo(22);
        assertThat(response.rewards().get(1).fishBalanceBefore())
                .isEqualTo(300L);
        assertThat(response.rewards().get(1).fishBalanceAfter())
                .isEqualTo(400L);
        verify(rankingRewardRepository)
                .findAllByMemberMemberIdAndRankingDateAndReadAtIsNullOrderByRankingRewardIdAsc(
                        1L,
                        LocalDate.of(2026, 10, 27)
                );
    }

    @Test
    @DisplayName("미확인 보상이 없으면 빈 배열을 반환한다")
    void getUnreadRewardsReturnsEmptyList() {
        when(rankingRewardRepository
                .findAllByMemberMemberIdAndRankingDateAndReadAtIsNullOrderByRankingRewardIdAsc(
                        1L,
                        LocalDate.of(2026, 10, 27)
                ))
                .thenReturn(List.of());

        UnreadRankingRewardsResponse response =
                rankingRewardService.getUnreadRewards(1L);

        assertThat(response.rewards()).isEmpty();
    }

    @Test
    @DisplayName("본인의 랭킹 보상을 현재 KST 시각으로 확인 처리한다")
    void markAsRead() {
        RankingReward reward = reward(
                10L,
                RankingType.STOCK,
                1,
                "1275.40",
                200L,
                300L
        );
        when(rankingRewardRepository.findByIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.of(reward));

        rankingRewardService.markAsRead(1L, 10L);

        assertThat(reward.getReadAt())
                .isEqualTo(LocalDateTime.of(2026, 10, 28, 9, 0));
    }

    @Test
    @DisplayName("다른 회원의 보상이나 존재하지 않는 보상은 확인할 수 없다")
    void rejectsUnknownReward() {
        when(rankingRewardRepository.findByIdAndMemberIdForUpdate(10L, 1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> rankingRewardService.markAsRead(1L, 10L))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RankingErrorCode.REWARD_NOT_FOUND)
                );
    }

    @Test
    @DisplayName("인증 회원 식별자가 없으면 미확인 보상을 조회할 수 없다")
    void rejectsUnauthenticatedMember() {
        assertThatThrownBy(() -> rankingRewardService.getUnreadRewards(null))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(GeneralErrorCode.UNAUTHORIZED)
                );

        verifyNoInteractions(rankingRewardRepository);
    }

    private RankingReward reward(
            Long rewardId,
            RankingType type,
            int position,
            String resultValue,
            long fishBalanceBefore,
            long fishBalanceAfter
    ) {
        RankingReward reward = RankingReward.create(
                mock(Member.class),
                type,
                LocalDate.of(2026, 10, 27),
                position,
                new BigDecimal(resultValue),
                100L,
                fishBalanceBefore,
                fishBalanceAfter
        );
        ReflectionTestUtils.setField(
                reward,
                "rankingRewardId",
                rewardId
        );
        return reward;
    }
}
