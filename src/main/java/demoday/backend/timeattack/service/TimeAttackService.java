package demoday.backend.timeattack.service;

import demoday.backend.activity.code.LearningStatus;
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
import demoday.backend.timeattack.code.TimeAttackStatus;
import demoday.backend.timeattack.domain.TimeAttackAnswer;
import demoday.backend.timeattack.domain.TimeAttackSession;
import demoday.backend.timeattack.dto.*;
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
import java.util.Objects;

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

    @Transactional
    public TimeAttackAnswerResponse submitAnswer(
            Long memberId,
            Long sessionId,
            TimeAttackAnswerRequest request
    ) {
        LocalDateTime now = LocalDateTime.now(KST);

        // 동일 세션의 동시 답안 제출 직렬화
        TimeAttackSession session =
                timeAttackSessionRepository
                        .findByIdAndMemberIdForUpdate(
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

        // 동일 문제의 중복 제출 차단
        if (timeAttackAnswerRepository
                .existsByTimeAttackSessionTimeAttackSessionIdAndQuestionQuestionId(
                        sessionId,
                        request.questionId()
                )) {
            throw new ProjectException(
                    TimeAttackErrorCode
                            .ANSWER_ALREADY_SUBMITTED
            );
        }

        // 서버가 계산한 현재 문제를 다시 조회
        QuizQuestion currentQuestion =
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

        // 임의의 문제 제출이나 문제 건너뛰기 차단
        if (!Objects.equals(
                currentQuestion.getQuestionId(),
                request.questionId()
        )) {
            throw new ProjectException(
                    TimeAttackErrorCode.QUESTION_NOT_CURRENT
            );
        }

        QuizOption selectedOption =
                quizOptionRepository
                        .findByOptionIdAndQuestionQuestionId(
                                request.selectedOptionId(),
                                request.questionId()
                        )
                        .orElseThrow(() ->
                                new ProjectException(
                                        TimeAttackErrorCode
                                                .OPTION_NOT_FOUND
                                )
                        );

        QuizOption correctOption =
                quizOptionRepository
                        .findByQuestionQuestionIdAndCorrectTrue(
                                request.questionId()
                        )
                        .orElseThrow(() ->
                                new ProjectException(
                                        TimeAttackErrorCode
                                                .CORRECT_OPTION_NOT_FOUND
                                )
                        );

        TimeAttackAnswer answer =
                TimeAttackAnswer.create(
                        session,
                        currentQuestion,
                        selectedOption,
                        now
                );

        timeAttackAnswerRepository.save(answer);

        if (Boolean.TRUE.equals(answer.getCorrect())) {
            session.increaseCorrectCount();
        }

        int consecutiveCorrectCount =
                calculateConsecutiveCorrectCount(sessionId);

        return TimeAttackAnswerResponse.of(
                session,
                answer,
                correctOption,
                consecutiveCorrectCount
        );
    }

    public TimeAttackCompleteResponse completeSession(
            Long memberId,
            Long sessionId
    ) {
        return transactionRetryExecutor.execute(
                () -> completeSessionInTransaction(
                        memberId,
                        sessionId
                )
        );
    }

    private TimeAttackCompleteResponse
    completeSessionInTransaction(
            Long memberId,
            Long sessionId
    ) {
        LocalDateTime now = LocalDateTime.now(KST);
        LocalDate completionDate = now.toLocalDate();

        // 회원 -> 세션 순서로 잠금 획득
        Member member = memberRepository
                .findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );

        TimeAttackSession session =
                timeAttackSessionRepository
                        .findByIdAndMemberIdForUpdate(
                                sessionId,
                                memberId
                        )
                        .orElseThrow(() ->
                                new ProjectException(
                                        TimeAttackErrorCode
                                                .SESSION_NOT_FOUND
                                )
                        );

        List<Boolean> correctResults =
                timeAttackAnswerRepository
                        .findCorrectResultsBySessionIdOrderByLatest(
                                sessionId
                        );

        // 동일 완료 요청은 학습 기록을 다시 반영하지 않고 기존 완료 결과를 반환
        if (session.getStatus()
                == TimeAttackStatus.COMPLETED) {
            return createCompleteResponse(
                    session,
                    correctResults
            );
        }

        // 60초가 지난 진행 중 세션만 정상 완료 가능
        session.complete(now);

        MemberDailyActivity activity =
                memberDailyActivityRepository
                        .findByMemberMemberIdAndActivityDate(
                                memberId,
                                completionDate
                        )
                        .orElseGet(() ->
                                MemberDailyActivity.create(
                                        member,
                                        completionDate
                                )
                        );

        // 같은 날 최초 학습 완료일 때만 연속 학습일을 갱신
        boolean learningCompleted =
                activity.completeLearning(now);

        if (learningCompleted) {
            boolean learnedYesterday =
                    memberDailyActivityRepository
                            .existsByMemberMemberIdAndActivityDateAndLearningStatusIn(
                                    memberId,
                                    completionDate.minusDays(1),
                                    List.of(
                                            LearningStatus.COMPLETED,
                                            LearningStatus.RECOVERED
                                    )
                            );

            member.completeLearning(learnedYesterday);
        }

        memberDailyActivityRepository.save(activity);

        return createCompleteResponse(
                session,
                correctResults
        );
    }

    private TimeAttackCompleteResponse createCompleteResponse(
            TimeAttackSession session,
            List<Boolean> correctResults
    ) {
        int maxConsecutiveCorrectCount =
                calculateMaxConsecutiveCorrectCount(
                        correctResults
                );

        return TimeAttackCompleteResponse.of(
                session,
                correctResults.size(),
                maxConsecutiveCorrectCount
        );
    }

    private int calculateMaxConsecutiveCorrectCount(
            List<Boolean> correctResults
    ) {
        int currentCount = 0;
        int maxCount = 0;

        for (Boolean correct : correctResults) {
            if (Boolean.TRUE.equals(correct)) {
                currentCount++;
                maxCount = Math.max(
                        maxCount,
                        currentCount
                );
                continue;
            }

            currentCount = 0;
        }

        return maxCount;
    }
}