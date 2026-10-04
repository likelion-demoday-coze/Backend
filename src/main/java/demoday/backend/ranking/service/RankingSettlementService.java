package demoday.backend.ranking.service;

import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.ranking.code.RankingErrorCode;
import demoday.backend.ranking.code.RankingType;
import demoday.backend.ranking.domain.RankingReward;
import demoday.backend.ranking.repository.RankingQueryRepository;
import demoday.backend.ranking.repository.RankingRewardRepository;
import demoday.backend.ranking.repository.projection.RankingWinnerRow;
import demoday.backend.ranking.repository.projection.StockClosingValueRow;
import demoday.backend.stock.domain.StockDailySnapshot;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static demoday.backend.ranking.code.RankingPolicy.KST;
import static demoday.backend.ranking.code.RankingPolicy.REWARD_FISH_AMOUNT;
import static demoday.backend.ranking.code.RankingPolicy.REWARD_MAX_RANK;

@Service
@RequiredArgsConstructor
public class RankingSettlementService {

    private final RankingQueryRepository rankingQueryRepository;
    private final RankingRewardRepository rankingRewardRepository;
    private final MemberRepository memberRepository;
    private final StockDailySnapshotRepository stockDailySnapshotRepository;
    private final FishService fishService;
    private final TransactionRetryExecutor transactionRetryExecutor;
    private final Clock clock;

    // KST 기준 전날의 주가 및 시간외거래 랭킹 확정
    public void settlePreviousDay() {
        LocalDate rankingDate =
                LocalDate.now(
                        clock.withZone(KST)
                ).minusDays(1);

        captureStockSnapshots(rankingDate);
        settle(rankingDate);
    }

    // 보상 정산과 별도 트랜잭션으로 마감 주가를 먼저 확정해 정산 재시도에도 같은 값을 사용
    private void captureStockSnapshots(
            LocalDate rankingDate
    ) {
        transactionRetryExecutor.execute(
                () -> {
                    LocalDateTime closedAt = rankingDate
                            .plusDays(1)
                            .atStartOfDay();

                    List<Member> members = memberRepository
                            .findAllByStatusAndCreatedBeforeForUpdate(
                                    MemberStatus.ACTIVE,
                                    closedAt
                            );

                    // 회원 잠금으로 주가 변경과 다중 서버의 스냅샷 생성을 직렬화한 뒤 확인
                    if (stockDailySnapshotRepository
                            .existsBySnapshotDate(rankingDate)) {
                        return null;
                    }

                    if (!members.isEmpty()) {
                        Map<Long, StockClosingValueRow> closingValues =
                                rankingQueryRepository
                                        .findStockClosingValues(closedAt)
                                        .stream()
                                        .collect(Collectors.toMap(
                                                StockClosingValueRow::getMemberId,
                                                Function.identity()
                                        ));

                        List<StockDailySnapshot> snapshots = members.stream()
                                .map(member -> StockDailySnapshot.create(
                                        member,
                                        rankingDate,
                                        closingValues
                                                .get(member.getMemberId())
                                                .getClosingStock()
                                ))
                                .toList();

                        stockDailySnapshotRepository.saveAll(snapshots);
                        stockDailySnapshotRepository.flush();
                    }

                    return null;
                }
        );
    }

    // 주어진 날짜의 두 랭킹을 하나의 트랜잭션에서 정산
    public void settle(
            LocalDate rankingDate
    ) {
        validateSettlementDate(
                rankingDate
        );

        transactionRetryExecutor.execute(
                () -> {
                    settleInTransaction(
                            rankingDate
                    );

                    return null;
                }
        );
    }

    // 주가 랭킹과 시간외거래 랭킹의 보상 대상을 조회하야 랭킹 유형별 보상 처리
    private void settleInTransaction(
            LocalDate rankingDate
    ) {
        List<RankingWinnerRow> stockWinners =
                rankingQueryRepository.findStockRewardTargets(
                        rankingDate,
                        REWARD_MAX_RANK
                );

        List<RankingWinnerRow> timeAttackWinners =
                rankingQueryRepository
                        .findTimeAttackRewardTargets(
                                rankingDate,
                                REWARD_MAX_RANK
                        );

        settleRankingType(
                rankingDate,
                RankingType.STOCK,
                stockWinners
        );

        settleRankingType(
                rankingDate,
                RankingType.TIME_ATTACK,
                timeAttackWinners
        );
    }

    // 한 랭킹 유형의 보상 대상자를 순서대로 처리
    private void settleRankingType(
            LocalDate rankingDate,
            RankingType rankingType,
            List<RankingWinnerRow> winners
    ) {
        for (RankingWinnerRow winner : winners) {
            settleWinner(
                    rankingDate,
                    rankingType,
                    winner
            );
        }
    }

    // 회원 한 명의 랭킹 보상 기록과 생선 지급 처리
    private void settleWinner(
            LocalDate rankingDate,
            RankingType rankingType,
            RankingWinnerRow winner
    ) {
        Member member =
                memberRepository
                        .findByIdForUpdate(
                                winner.getMemberId()
                        )
                        .orElse(null);

        if (member == null
                || member.getStatus()
                != MemberStatus.ACTIVE) {
            return;
        }

        boolean alreadyRewarded =
                rankingRewardRepository
                        .existsByMemberMemberIdAndRankingTypeAndRankingDate(
                                member.getMemberId(),
                                rankingType,
                                rankingDate
                        );

        if (alreadyRewarded) {
            return;
        }

        RankingReward reward =
                rankingRewardRepository.save(
                        RankingReward.create(
                                member,
                                rankingType,
                                rankingDate,
                                winner.getRankingPosition()
                                        .intValue()
                        )
                );

        fishService.credit(
                member.getMemberId(),
                REWARD_FISH_AMOUNT,
                FishTransactionType.RANKING_REWARD,
                reward.getRankingRewardId(),
                createIdempotencyKey(
                        member.getMemberId(),
                        rankingType,
                        rankingDate
                )
        );
    }

    // 오늘이나 미래 날짜의 랭킹을 미리 정산하지 못하게 함
    private void validateSettlementDate(
            LocalDate rankingDate
    ) {
        LocalDate today =
                LocalDate.now(
                        clock.withZone(KST)
                );

        if (rankingDate == null
                || !rankingDate.isBefore(today)) {
            throw new ProjectException(
                    RankingErrorCode
                            .INVALID_SETTLEMENT_DATE
            );
        }
    }

    // 같은 회원·랭킹 유형·날짜의 생선 보상 중복 지급되지 않도록 멱등성 키를 생성
    private String createIdempotencyKey(
            Long memberId,
            RankingType rankingType,
            LocalDate rankingDate
    ) {
        return "RANKING_REWARD:"
                + rankingType.name()
                + ":"
                + rankingDate
                + ":"
                + memberId;
    }
}
