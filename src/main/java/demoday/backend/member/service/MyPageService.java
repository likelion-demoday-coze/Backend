package demoday.backend.member.service;

import demoday.backend.activity.code.LearningStatus;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.character.code.CharacterStage;
import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.dto.MyPageResponse;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.member.repository.QuizStatisticsProjection;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.store.repository.MemberItemRepository;
import demoday.backend.timeattack.code.TimeAttackStatus;
import demoday.backend.timeattack.repository.TimeAttackAnswerRepository;
import demoday.backend.timeattack.repository.TimeAttackSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPageService {

    private static final String RECOVERY_ITEM_CODE = "RECOVERY";

    private final MemberRepository memberRepository;
    private final MemberItemRepository memberItemRepository;
    private final MemberPassRepository memberPassRepository;
    private final MemberDailyActivityRepository dailyActivityRepository;
    private final StockChangeRepository stockChangeRepository;
    private final TimeAttackSessionRepository timeAttackSessionRepository;
    private final DailyQuizAttemptRepository dailyQuizAttemptRepository;
    private final TimeAttackAnswerRepository timeAttackAnswerRepository;
    private final Clock clock;

    public MyPageResponse getMyPage(Long memberId) {

        Member member = findActiveMember(memberId);

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        MemberPass activePass = memberPassRepository.findActivePass(
                memberId,
                PassStatus.ACTIVE,
                now
        ).orElse(null);

        int recoveryTicketCount = memberItemRepository
                .findByMemberMemberIdAndItemItemCode(
                        memberId,
                        RECOVERY_ITEM_CODE
                )
                .map(memberItem -> memberItem.getQuantity())
                .orElse(0);

        List<LocalDate> learnedDates =
                dailyActivityRepository.findLearnedDatesByMemberIdAndStatusIn(
                        memberId,
                        List.of(
                                LearningStatus.COMPLETED,
                                LearningStatus.RECOVERED
                        )
                );

        BigDecimal highestStock = stockChangeRepository
                .findHighestStockByMemberId(memberId)
                .map(stock -> stock.max(member.getCurrentStock()))
                .orElse(member.getCurrentStock());

        int highestTimeAttackCorrectCount =
                timeAttackSessionRepository
                        .findHighestCorrectCountByMemberIdAndStatus(
                                memberId,
                                TimeAttackStatus.COMPLETED
                        )
                        .orElse(0);

        QuizStatisticsProjection dailyQuizStatistics =
                dailyQuizAttemptRepository
                        .findStatisticsByMemberIdAndAttemptType(
                                memberId,
                                DailyQuizAttemptType.ORIGINAL
                        );

        QuizStatisticsProjection timeAttackStatistics =
                timeAttackAnswerRepository
                        .findStatisticsByMemberId(memberId);

        long totalAnsweredCount =
                valueOf(dailyQuizStatistics.getTotalCount())
                        + valueOf(timeAttackStatistics.getTotalCount());

        long totalCorrectCount =
                valueOf(dailyQuizStatistics.getCorrectCount())
                        + valueOf(timeAttackStatistics.getCorrectCount());

        BigDecimal accuracyPercent = calculateAccuracy(
                totalAnsweredCount,
                totalCorrectCount
        );

        return new MyPageResponse(
                new MyPageResponse.Profile(
                        member.getMemberId(),
                        member.getNickname(),
                        calculateJoinedDays(
                                member.getCreatedAt(),
                                today
                        ),
                        CharacterStage.fromStock(
                                member.getCurrentStock()
                        ),
                        activePass != null,
                        activePass == null
                                ? null
                                : activePass.getExpiresAt()
                ),
                new MyPageResponse.Assets(
                        member.getFishBalance(),
                        recoveryTicketCount,
                        member.getCurrentStock()
                ),
                new MyPageResponse.Records(
                        member.getCurrentStreak(),
                        calculateHighestStreak(learnedDates),
                        highestStock,
                        highestTimeAttackCorrectCount,
                        totalAnsweredCount,
                        totalCorrectCount,
                        accuracyPercent
                )
        );
    }

    private Member findActiveMember(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(
                    MemberErrorCode.INACTIVE_MEMBER
            );
        }

        return member;
    }

    private Long calculateJoinedDays(
            LocalDateTime createdAt,
            LocalDate today
    ) {
        if (createdAt == null) {
            return null;
        }

        return ChronoUnit.DAYS.between(
                createdAt.toLocalDate(),
                today
        ) + 1;
    }

    private int calculateHighestStreak(
            List<LocalDate> learnedDates
    ) {
        if (learnedDates.isEmpty()) {
            return 0;
        }

        int currentStreak = 1;
        int highestStreak = 1;

        for (int index = 1; index < learnedDates.size(); index++) {
            LocalDate previousDate = learnedDates.get(index - 1);
            LocalDate currentDate = learnedDates.get(index);

            if (currentDate.equals(previousDate.plusDays(1))) {
                currentStreak++;
                highestStreak = Math.max(
                        highestStreak,
                        currentStreak
                );
                continue;
            }

            currentStreak = 1;
        }

        return highestStreak;
    }

    private BigDecimal calculateAccuracy(
            long totalAnsweredCount,
            long totalCorrectCount
    ) {
        if (totalAnsweredCount == 0) {
            return BigDecimal.ZERO.setScale(2);
        }

        return BigDecimal.valueOf(totalCorrectCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(totalAnsweredCount),
                        2,
                        RoundingMode.HALF_UP
                );
    }

    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
