package demoday.backend.ranking.domain;

import demoday.backend.member.domain.Member;
import demoday.backend.ranking.code.RankingType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RankingRewardTest {

    private final Member member = mock(Member.class);

    @Test
    @DisplayName("랭킹 결과와 FISH 지급 전후 잔액을 스냅샷으로 저장한다")
    void create() {
        RankingReward reward = RankingReward.create(
                member,
                RankingType.STOCK,
                LocalDate.of(2026, 10, 27),
                1,
                new BigDecimal("1275.40"),
                100L,
                200L,
                300L
        );

        assertThat(reward.getMember()).isSameAs(member);
        assertThat(reward.getRankingType()).isEqualTo(RankingType.STOCK);
        assertThat(reward.getRankingResultValue())
                .isEqualByComparingTo("1275.40");
        assertThat(reward.getRewardFishAmount()).isEqualTo(100L);
        assertThat(reward.getFishBalanceBefore()).isEqualTo(200L);
        assertThat(reward.getFishBalanceAfter()).isEqualTo(300L);
        assertThat(reward.getReadAt()).isNull();
    }

    @Test
    @DisplayName("보상 확인 처리는 최초 확인 시각을 유지하며 멱등하게 동작한다")
    void markAsReadIsIdempotent() {
        RankingReward reward = createReward();
        LocalDateTime firstReadAt =
                LocalDateTime.of(2026, 10, 28, 9, 0);

        reward.markAsRead(firstReadAt);
        reward.markAsRead(firstReadAt.plusHours(1));

        assertThat(reward.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    @DisplayName("최초 확인 시각은 필수이다")
    void rejectsNullReadAt() {
        RankingReward reward = createReward();

        assertThatThrownBy(() -> reward.markAsRead(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("확인 시각은 필수입니다.");
    }

    private RankingReward createReward() {
        return RankingReward.create(
                member,
                RankingType.TIME_ATTACK,
                LocalDate.of(2026, 10, 27),
                2,
                new BigDecimal("22.00"),
                100L,
                200L,
                300L
        );
    }
}
