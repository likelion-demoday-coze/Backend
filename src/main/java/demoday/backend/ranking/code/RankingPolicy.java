package demoday.backend.ranking.code;

import java.time.ZoneId;

public final class RankingPolicy {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static final int REWARD_MAX_RANK = 3;
    public static final long REWARD_FISH_AMOUNT = 100L;

    private RankingPolicy() {
    }

    public static boolean isRewardTarget(int rank) {
        return rank >= 1 && rank <= REWARD_MAX_RANK;
    }
}
