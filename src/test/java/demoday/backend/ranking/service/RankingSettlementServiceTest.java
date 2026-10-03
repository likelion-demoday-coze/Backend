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
import demoday.backend.stock.domain.StockDailySnapshot;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static demoday.backend.ranking.code.RankingPolicy.REWARD_FISH_AMOUNT;
import static demoday.backend.ranking.code.RankingPolicy.REWARD_MAX_RANK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RankingSettlementServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-03T00:00:00Z"),
            KST
    );
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 10, 2);

    @Mock RankingQueryRepository rankingQueryRepository;
    @Mock RankingRewardRepository rankingRewardRepository;
    @Mock MemberRepository memberRepository;
    @Mock StockDailySnapshotRepository stockDailySnapshotRepository;
    @Mock FishService fishService;
    @Mock TransactionRetryExecutor transactionRetryExecutor;

    private RankingSettlementService rankingSettlementService;
    private final AtomicLong rewardId = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        rankingSettlementService = new RankingSettlementService(
                rankingQueryRepository,
                rankingRewardRepository,
                memberRepository,
                stockDailySnapshotRepository,
                fishService,
                transactionRetryExecutor,
                CLOCK
        );

        lenient().when(transactionRetryExecutor.execute(any()))
                .thenAnswer(invocation -> {
                    Supplier<?> operation = invocation.getArgument(0);
                    return operation.get();
                });

        lenient().when(stockDailySnapshotRepository
                .existsBySnapshotDate(YESTERDAY))
                .thenReturn(true);

        lenient().when(rankingRewardRepository.save(any(RankingReward.class)))
                .thenAnswer(invocation -> {
                    RankingReward reward = invocation.getArgument(0);
                    ReflectionTestUtils.setField(
                            reward,
                            "rankingRewardId",
                            rewardId.getAndIncrement()
                    );
                    return reward;
                });
    }

    @Test
    @DisplayName("전날 주가 및 타임어택 수상자를 조회해 유형별 보상을 지급한다")
    void settlePreviousDay() {
        RankingWinnerRow stockWinner = winner(1L, 1L);
        RankingWinnerRow timeAttackWinner = winner(2L, 3L);
        Member stockMember = activeMember(1L);
        Member timeAttackMember = activeMember(2L);

        when(rankingQueryRepository.findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        ))
                .thenReturn(List.of(stockWinner));
        when(rankingQueryRepository.findTimeAttackRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of(timeAttackWinner));
        when(memberRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(stockMember));
        when(memberRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(timeAttackMember));
        when(rankingRewardRepository
                .existsByMemberMemberIdAndRankingTypeAndRankingDate(
                        1L, RankingType.STOCK, YESTERDAY
                )).thenReturn(false);
        when(rankingRewardRepository
                .existsByMemberMemberIdAndRankingTypeAndRankingDate(
                        2L, RankingType.TIME_ATTACK, YESTERDAY
                )).thenReturn(false);

        rankingSettlementService.settlePreviousDay();

        ArgumentCaptor<RankingReward> rewardCaptor =
                ArgumentCaptor.forClass(RankingReward.class);
        verify(rankingRewardRepository, times(2)).save(rewardCaptor.capture());

        assertThat(rewardCaptor.getAllValues())
                .extracting(
                        RankingReward::getRankingType,
                        RankingReward::getRankingDate,
                        RankingReward::getRankingPosition
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                RankingType.STOCK, YESTERDAY, 1
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                RankingType.TIME_ATTACK, YESTERDAY, 3
                        )
                );

        verify(fishService).credit(
                1L,
                REWARD_FISH_AMOUNT,
                FishTransactionType.RANKING_REWARD,
                100L,
                "RANKING_REWARD:STOCK:2026-10-02:1"
        );
        verify(fishService).credit(
                2L,
                REWARD_FISH_AMOUNT,
                FishTransactionType.RANKING_REWARD,
                101L,
                "RANKING_REWARD:TIME_ATTACK:2026-10-02:2"
        );
        verify(transactionRetryExecutor, times(2)).execute(any());
    }

    @Test
    @DisplayName("전날 정산 전에 활성 회원의 마감 주가 스냅샷을 별도 트랜잭션으로 저장한다")
    void capturesStockSnapshotsBeforeSettlement() {
        Member first = activeMemberWithStock(1L, "300.00");
        Member second = activeMemberWithStock(2L, "200.00");

        when(stockDailySnapshotRepository.existsBySnapshotDate(YESTERDAY))
                .thenReturn(false, false);
        when(memberRepository.findAllByStatusForUpdate(MemberStatus.ACTIVE))
                .thenReturn(List.of(first, second));
        when(rankingQueryRepository.findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of());
        when(rankingQueryRepository.findTimeAttackRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of());

        rankingSettlementService.settlePreviousDay();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<StockDailySnapshot>> snapshotsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(stockDailySnapshotRepository).saveAll(
                snapshotsCaptor.capture()
        );
        verify(stockDailySnapshotRepository).flush();

        assertThat(snapshotsCaptor.getValue())
                .extracting(
                        snapshot -> snapshot.getMember().getMemberId(),
                        StockDailySnapshot::getSnapshotDate,
                        StockDailySnapshot::getStockValue
                )
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                1L, YESTERDAY, new java.math.BigDecimal("300.00")
                        ),
                        org.assertj.core.groups.Tuple.tuple(
                                2L, YESTERDAY, new java.math.BigDecimal("200.00")
                        )
                );

        verify(transactionRetryExecutor, times(2)).execute(any());
        var order = inOrder(
                stockDailySnapshotRepository,
                rankingQueryRepository
        );
        order.verify(stockDailySnapshotRepository).flush();
        order.verify(rankingQueryRepository).findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        );
    }

    @Test
    @DisplayName("전날 스냅샷이 이미 있으면 값을 덮어쓰지 않고 기존 값으로 재정산한다")
    void reusesExistingStockSnapshots() {
        when(stockDailySnapshotRepository.existsBySnapshotDate(YESTERDAY))
                .thenReturn(true);
        when(rankingQueryRepository.findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of());
        when(rankingQueryRepository.findTimeAttackRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of());

        rankingSettlementService.settlePreviousDay();

        verify(memberRepository, never())
                .findAllByStatusForUpdate(any());
        verify(stockDailySnapshotRepository, never()).saveAll(any());
        verify(stockDailySnapshotRepository, never()).flush();
        verify(rankingQueryRepository).findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        );
    }

    @Test
    @DisplayName("스냅샷 저장이 실패하면 보상 정산을 실행하지 않는다")
    void doesNotSettleWhenSnapshotCaptureFails() {
        Member member = activeMemberWithStock(1L, "300.00");
        when(stockDailySnapshotRepository.existsBySnapshotDate(YESTERDAY))
                .thenReturn(false, false);
        when(memberRepository.findAllByStatusForUpdate(MemberStatus.ACTIVE))
                .thenReturn(List.of(member));
        doThrow(new RuntimeException("스냅샷 저장 실패"))
                .when(stockDailySnapshotRepository)
                .flush();

        assertThatThrownBy(rankingSettlementService::settlePreviousDay)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("스냅샷 저장 실패");

        verifyNoInteractions(rankingQueryRepository);
        verifyNoInteractions(fishService);
        verify(transactionRetryExecutor).execute(any());
    }

    @Test
    @DisplayName("동점으로 같은 순위를 받은 모든 수상자에게 보상을 지급한다")
    void rewardsEveryTiedWinner() {
        RankingWinnerRow first = winner(1L, 1L);
        RankingWinnerRow tied = winner(2L, 1L);
        Member firstMember = activeMember(1L);
        Member tiedMember = activeMember(2L);

        when(rankingQueryRepository.findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        ))
                .thenReturn(List.of(first, tied));
        when(rankingQueryRepository.findTimeAttackRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of());
        when(memberRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(firstMember));
        when(memberRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(tiedMember));
        when(rankingRewardRepository
                .existsByMemberMemberIdAndRankingTypeAndRankingDate(
                        anyLong(), eq(RankingType.STOCK), eq(YESTERDAY)
                )).thenReturn(false);

        rankingSettlementService.settle(YESTERDAY);

        verify(rankingRewardRepository, times(2)).save(any(RankingReward.class));
        verify(fishService, times(2)).credit(
                anyLong(),
                eq((long) REWARD_FISH_AMOUNT),
                eq(FishTransactionType.RANKING_REWARD),
                anyLong(),
                anyString()
        );
    }

    @Test
    @DisplayName("이미 같은 날짜와 유형으로 지급된 회원은 중복 지급하지 않는다")
    void skipsAlreadyRewardedWinner() {
        RankingWinnerRow winner = winner(1L, 1L);
        Member member = activeMember(1L);

        when(rankingQueryRepository.findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        ))
                .thenReturn(List.of(winner));
        when(rankingQueryRepository.findTimeAttackRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of());
        when(memberRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(member));
        when(rankingRewardRepository
                .existsByMemberMemberIdAndRankingTypeAndRankingDate(
                        1L, RankingType.STOCK, YESTERDAY
                )).thenReturn(true);

        rankingSettlementService.settle(YESTERDAY);

        verify(rankingRewardRepository, never()).save(any());
        verifyNoInteractions(fishService);
    }

    @Test
    @DisplayName("정산 사이에 탈퇴했거나 존재하지 않게 된 회원은 보상에서 제외한다")
    void skipsMissingAndInactiveWinner() {
        RankingWinnerRow missing = winner(1L, 1L);
        RankingWinnerRow inactive = winner(2L, 2L);
        Member inactiveMember = mock(Member.class);

        when(rankingQueryRepository.findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        ))
                .thenReturn(List.of(missing, inactive));
        when(rankingQueryRepository.findTimeAttackRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of());
        when(memberRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.empty());
        when(memberRepository.findByIdForUpdate(2L))
                .thenReturn(Optional.of(inactiveMember));
        when(inactiveMember.getStatus()).thenReturn(MemberStatus.WITHDRAWN);

        rankingSettlementService.settle(YESTERDAY);

        verifyNoInteractions(fishService);
        verify(rankingRewardRepository, never()).save(any());
    }

    @Test
    @DisplayName("같은 회원이 두 랭킹의 수상자이면 유형별로 각각 지급한다")
    void rewardsSameMemberForBothRankingTypes() {
        RankingWinnerRow stockWinner = winner(1L, 1L);
        RankingWinnerRow timeAttackWinner = winner(1L, 2L);
        Member member = activeMember(1L);

        when(rankingQueryRepository.findStockRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        ))
                .thenReturn(List.of(stockWinner));
        when(rankingQueryRepository.findTimeAttackRewardTargets(
                YESTERDAY, REWARD_MAX_RANK
        )).thenReturn(List.of(timeAttackWinner));
        when(memberRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(member));
        when(rankingRewardRepository
                .existsByMemberMemberIdAndRankingTypeAndRankingDate(
                        eq(1L), any(RankingType.class), eq(YESTERDAY)
                )).thenReturn(false);

        rankingSettlementService.settle(YESTERDAY);

        verify(rankingRewardRepository, times(2)).save(any(RankingReward.class));
        verify(fishService, times(2)).credit(
                eq(1L),
                eq((long) REWARD_FISH_AMOUNT),
                eq(FishTransactionType.RANKING_REWARD),
                anyLong(),
                anyString()
        );
    }

    @Test
    @DisplayName("오늘 날짜는 미리 정산할 수 없다")
    void rejectsToday() {
        assertInvalidDate(LocalDate.of(2026, 10, 3));
    }

    @Test
    @DisplayName("미래 날짜는 미리 정산할 수 없다")
    void rejectsFutureDate() {
        assertInvalidDate(LocalDate.of(2026, 10, 4));
    }

    @Test
    @DisplayName("정산 날짜가 없으면 거절한다")
    void rejectsNullDate() {
        assertInvalidDate(null);
    }

    private void assertInvalidDate(LocalDate date) {
        assertThatThrownBy(() -> rankingSettlementService.settle(date))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(RankingErrorCode.INVALID_SETTLEMENT_DATE)
                );

        verifyNoInteractions(
                rankingQueryRepository,
                rankingRewardRepository,
                memberRepository,
                fishService,
                transactionRetryExecutor
        );
    }

    private RankingWinnerRow winner(Long memberId, Long position) {
        RankingWinnerRow winner = mock(RankingWinnerRow.class);
        lenient().when(winner.getMemberId()).thenReturn(memberId);
        lenient().when(winner.getRankingPosition()).thenReturn(position);
        return winner;
    }

    private Member activeMember(Long memberId) {
        Member member = mock(Member.class);
        lenient().when(member.getMemberId()).thenReturn(memberId);
        lenient().when(member.getStatus()).thenReturn(MemberStatus.ACTIVE);
        return member;
    }

    private Member activeMemberWithStock(Long memberId, String stock) {
        Member member = activeMember(memberId);
        lenient().when(member.getCurrentStock())
                .thenReturn(new java.math.BigDecimal(stock));
        return member;
    }
}
