package demoday.backend.timeattack.service;

import demoday.backend.activity.domain.MemberDailyActivity;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.timeattack.code.TimeAttackErrorCode;
import demoday.backend.timeattack.domain.TimeAttackSession;
import demoday.backend.timeattack.dto.TimeAttackQuestionResponse;
import demoday.backend.timeattack.dto.TimeAttackSessionCreateResponse;
import demoday.backend.timeattack.dto.TimeAttackTodayResponse;
import demoday.backend.timeattack.repository.TimeAttackAnswerRepository;
import demoday.backend.timeattack.repository.TimeAttackSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TimeAttackService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final int DAILY_ATTEMPT_LIMIT = 3;
    private static final long TIME_ATTACK_FISH_COST = 100L;

    private final MemberRepository memberRepository;
    private final MemberDailyActivityRepository memberDailyActivityRepository;
    private final MemberPassRepository memberPassRepository;
    private final TimeAttackSessionRepository timeAttackSessionRepository;
    private final FishService fishService;
    private final TransactionRetryExecutor transactionRetryExecutor;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final TimeAttackAnswerRepository timeAttackAnswerRepository;

    @Transactional(readOnly = true)
    public TimeAttackTodayResponse getTodayAvailability(
            Long memberId
    ) {
        LocalDate today = LocalDate.now(KST);

        int usedAttemptCount = memberDailyActivityRepository
                .findByMemberMemberIdAndActivityDate(
                        memberId,
                        today
                )
                .map(MemberDailyActivity::getTimeAttackAttemptCount)
                .orElse(0);

        int remainingAttemptCount = Math.max(
                DAILY_ATTEMPT_LIMIT - usedAttemptCount,
                0
        );

        return new TimeAttackTodayResponse(
                DAILY_ATTEMPT_LIMIT,
                usedAttemptCount,
                remainingAttemptCount
        );
    }

    public TimeAttackSessionCreateResponse createSession(
            Long memberId
    ) {
        return transactionRetryExecutor.execute(
                () -> createSessionInTransaction(memberId)
        );
    }

    private TimeAttackSessionCreateResponse createSessionInTransaction(
            Long memberId
    ) {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDate today = now.toLocalDate();

        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(GeneralErrorCode.NOT_FOUND)
                );

        MemberDailyActivity activity =
                memberDailyActivityRepository
                        .findByMemberMemberIdAndActivityDate(
                                memberId,
                                today
                        )
                        .orElseGet(() ->
                                MemberDailyActivity.create(
                                        member,
                                        today
                                )
                        );

        if (!activity.recordTimeAttackAttempt(DAILY_ATTEMPT_LIMIT)) {
            throw new ProjectException(
                    TimeAttackErrorCode.DAILY_ATTEMPT_LIMIT_EXCEEDED
            );
        }

        memberDailyActivityRepository.save(activity);

        boolean passApplied = memberPassRepository
                .findActivePass(
                        memberId,
                        PassStatus.ACTIVE,
                        now
                )
                .isPresent();

        TimeAttackSession session =
                TimeAttackSession.create(
                        member,
                        today,
                        now,
                        passApplied
                );

        timeAttackSessionRepository.save(session);

        if (!passApplied) {
            fishService.debit(
                    memberId,
                    TIME_ATTACK_FISH_COST,
                    FishTransactionType.TIME_ATTACK_COST,
                    session.getTimeAttackSessionId(),
                    "TIME_ATTACK_SESSION:" + session.getTimeAttackSessionId()
            );
        }

        return TimeAttackSessionCreateResponse.from(session);
    }

    @Transactional(readOnly = true)
    public TimeAttackQuestionResponse getNextQuestion(
            Long memberId,
            Long sessionId
    ) {
        LocalDateTime now = LocalDateTime.now(KST);

        TimeAttackSession session =
                timeAttackSessionRepository
                        .findByTimeAttackSessionIdAndMemberMemberId(
                                sessionId,
                                memberId
                        )
                        .orElseThrow(() ->
                                new ProjectException(
                                        TimeAttackErrorCode
                                                .SESSION_NOT_FOUND
                                )
                        );

        session.validateAnswerable(now);

        QuizQuestion question =
                quizQuestionRepository
                        .findNextTimeAttackQuestion(
                                sessionId,
                                QuizCategory.PREVIEW,
                                PageRequest.of(0, 1)
                        )
                        .stream()
                        .findFirst()
                        .orElseThrow(() ->
                                new ProjectException(
                                        TimeAttackErrorCode
                                                .NO_AVAILABLE_QUESTION
                                )
                        );

        List<QuizOption> options =
                quizOptionRepository
                        .findAllByQuestionQuestionIdOrderByOptionNumberAsc(
                                question.getQuestionId()
                        );

        int consecutiveCorrectCount =
                calculateConsecutiveCorrectCount(sessionId);

        return TimeAttackQuestionResponse.of(
                session,
                consecutiveCorrectCount,
                question,
                options
        );
    }

    private int calculateConsecutiveCorrectCount(
            Long sessionId
    ) {
        List<Boolean> correctResults =
                timeAttackAnswerRepository
                        .findCorrectResultsBySessionIdOrderByLatest(
                                sessionId
                        );

        int consecutiveCorrectCount = 0;

        for (Boolean correct : correctResults) {
            if (!Boolean.TRUE.equals(correct)) {
                break;
            }

            consecutiveCorrectCount++;
        }

        return consecutiveCorrectCount;
    }
}