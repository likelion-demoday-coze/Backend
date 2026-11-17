package demoday.backend.dailyquiz.service;

import demoday.backend.activity.code.LearningStatus;
import demoday.backend.activity.domain.MemberDailyActivity;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizAttempt;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerResponse;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerResult;
import demoday.backend.dailyquiz.dto.answer.DailyQuizRetryAnswerResponse;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.domain.MemberQuestionHistory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.repository.MemberQuestionHistoryRepository;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.service.StockService;
import demoday.backend.streak.service.StreakPenaltyService;
import demoday.backend.streak.service.StreakRecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static demoday.backend.dailyquiz.policy.DailyQuizPolicy.*;
import static demoday.backend.dailyquiz.policy.DailyQuizPolicy.QUESTIONS_PER_SESSION;

@Service
@RequiredArgsConstructor
public class DailyQuizAnswerService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final DailyQuizSessionRepository dailyQuizSessionRepository;
    private final DailyQuizSessionQuestionRepository sessionQuestionRepository;
    private final DailyQuizAttemptRepository dailyQuizAttemptRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final MemberDailyActivityRepository memberDailyActivityRepository;
    private final MemberQuestionHistoryRepository memberQuestionHistoryRepository;
    private final StockService stockService;
    private final StreakPenaltyService streakPenaltyService;
    private final StreakRecoveryService streakRecoveryService;
    private final TransactionRetryExecutor transactionRetryExecutor;
    private final Clock clock;

    // 답안 제출 요청이 원본인지 재풀이인지 구분
    public DailyQuizAnswerResult submitAnswer(
            Long memberId,
            Long sessionId,
            Long sessionQuestionId,
            DailyQuizAnswerRequest request
    ) {
        return transactionRetryExecutor.execute(() -> switch (request.attemptType()) {
            case ORIGINAL -> submitOriginalAnswer(
                    memberId,
                    sessionId,
                    sessionQuestionId,
                    request
            );
            case RETRY -> submitRetryAnswer(
                    memberId,
                    sessionId,
                    sessionQuestionId,
                    request
            );
        });
    }

    // 원본 문제 답안을 최초 제출
    private DailyQuizAnswerResponse submitOriginalAnswer(
            Long memberId,
            Long sessionId,
            Long sessionQuestionId,
            DailyQuizAnswerRequest request
    ) {
        // 회원 비관적 락 조회
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(GeneralErrorCode.NOT_FOUND)
                );

        DailyQuizSession session = findSessionForUpdate(sessionId, memberId);

        // 잠금 대기가 자정을 넘겼다면 잠금을 획득한 후의 날짜를 모든 판정에 사용한다.
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));

        streakPenaltyService.applyDuePenaltyInTransaction(memberId, now.toLocalDate());

        // 세션 만료 확인
        validateSessionNotExpired(session, now);

        // 세션 문제 조회
        DailyQuizSessionQuestion sessionQuestion = findSessionQuestion(
                sessionQuestionId,
                sessionId
        );

        // 기존 원본 답안 확인
        Optional<DailyQuizAttempt> existingAttempt =
                dailyQuizAttemptRepository
                        .findBySessionQuestionSessionQuestionIdAndAttemptType(
                                sessionQuestionId,
                                DailyQuizAttemptType.ORIGINAL
                        );

        if (existingAttempt.isPresent()) {
            return handleExistingOriginalAttempt(
                    existingAttempt.get(),
                    request.selectedOptionId(),
                    session,
                    member
            );
        }

        // 세션 상태 확인
        if (session.getStatus() != DailyQuizSessionStatus.IN_PROGRESS) {
            throw new ProjectException(
                    DailyQuizErrorCode.SESSION_ALREADY_COMPLETED
            );
        }

        // 선택지 조회
        QuizOption selectedOption = findAndValidateSelectedOption(
                request.selectedOptionId(),
                sessionQuestion
        );

        // 오늘의 활동 조회 또는 생성
        MemberDailyActivity dailyActivity =
                memberDailyActivityRepository
                        .findByMemberMemberIdAndActivityDate(
                                memberId,
                                now.toLocalDate()
                        )
                        .orElseGet(() ->
                                MemberDailyActivity.create(
                                        member,
                                        now.toLocalDate()
                                )
                        );

        // 주가 상승 기회 제한 결정
        int stockOpportunityLimit = stockOpportunityQuestionLimit(
                Boolean.TRUE.equals(session.getPassApplied())
        );

        // 원본 답안 횟수 및 상승 기회 기록
        boolean stockOpportunityAvailable =
                dailyActivity.recordOriginalAnswer(stockOpportunityLimit);

        Integer stockIncreasePercent = null;
        BigDecimal stockBefore = null;
        BigDecimal stockAfter = member.getCurrentStock();

        // 정답이면 주가 상승
        if (Boolean.TRUE.equals(selectedOption.getCorrect())
                && stockOpportunityAvailable) {
            stockBefore = member.getCurrentStock();

            stockIncreasePercent = ThreadLocalRandom.current().nextInt(
                    MIN_STOCK_INCREASE_PERCENT,
                    MAX_STOCK_INCREASE_PERCENT + 1
            );

            stockAfter = stockBefore.multiply(BigDecimal.ONE.add(
                    BigDecimal.valueOf(stockIncreasePercent).movePointLeft(2)
            )).setScale(2, RoundingMode.HALF_UP);
        }

        // 원본 답안 저장
        DailyQuizAttempt attempt = DailyQuizAttempt.create(
                sessionQuestion,
                selectedOption,
                DailyQuizAttemptType.ORIGINAL,
                stockIncreasePercent,
                stockAfter,
                now
        );

        dailyQuizAttemptRepository.save(attempt);

        // 실제 주가가 상승한 경우 변동 이력 저장
        if (stockBefore != null) {
            stockService.changeStock(memberId, stockBefore, stockAfter,
                    StockChangeType.QUIZ_CORRECT, attempt.getDailyQuizAttemptId(),
                    "DAILY_QUIZ_ATTEMPT:" + attempt.getDailyQuizAttemptId());
        }

        // 활동과 문제 풀이 이력 저장
        memberDailyActivityRepository.save(dailyActivity);
        memberQuestionHistoryRepository.save(
                MemberQuestionHistory.create(
                        member,
                        sessionQuestion.getQuestion()
                )
        );

        // 원본 문제 제출 개수 확인
        long answeredCount = dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
                        sessionId,
                        DailyQuizAttemptType.ORIGINAL
                );

        // 원본 문제 5개를 모두 제출한 경우
        if (answeredCount == QUESTIONS_PER_SESSION) {
            session.completeOriginal(
                    member.getCurrentStock()
            );

            // 오늘 최초 학습 완료인지 확인
            boolean learningCompleted =
                    dailyActivity.completeLearning(now);

            // 같은 날 최초 학습 완료일 때만 연속 학습일 갱신
            if (learningCompleted) {
                boolean learnedYesterday =
                        memberDailyActivityRepository
                                .existsByMemberMemberIdAndActivityDateAndLearningStatusIn(
                                        memberId,
                                        now.toLocalDate().minusDays(1),
                                        List.of(
                                                LearningStatus.COMPLETED,
                                                LearningStatus.RECOVERED
                                        )
                                );

                member.completeLearning(learnedYesterday, now.toLocalDate());
                streakRecoveryService.completeForLearningInTransaction(memberId, now);
            }

            // 원본 오답 개수 확인
            long incorrectCount =
                    dailyQuizAttemptRepository
                            .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeAndCorrectFalse(
                                    sessionId,
                                    DailyQuizAttemptType.ORIGINAL
                            );

            // 원본 문제를 모두 맞혔다면 재풀이 없이 세션 완료
            if (incorrectCount == 0) {
                session.complete();
            }
        }

        // 정답 선택지 조회
        QuizOption correctOption = findCorrectOption(
                sessionQuestion.getQuestion().getQuestionId()
        );

        // 응답 반환
        return DailyQuizAnswerResponse.of(
                attempt,
                correctOption.getOptionId(),
                sessionQuestion.getQuestion().getExplanation(),
                member.getCurrentStock(),
                answeredCount,
                QUESTIONS_PER_SESSION,
                session.getStatus()
        );
    }

    // 같은 답안 제출 요청 중복으로 들어왔을 때 처리 (네트워크 재시도, 사용자의 연속 클릭 등..)
    private DailyQuizAnswerResponse handleExistingOriginalAttempt(
            DailyQuizAttempt existingAttempt,
            Long selectedOptionId,
            DailyQuizSession session,
            Member member
    ) {
        if (!Objects.equals(
                existingAttempt.getSelectedOption().getOptionId(),
                selectedOptionId
        )) {
            throw new ProjectException(
                    DailyQuizErrorCode.ANSWER_ALREADY_SUBMITTED
            );
        }

        Long questionId = existingAttempt
                .getSessionQuestion()
                .getQuestion()
                .getQuestionId();

        QuizOption correctOption = findCorrectOption(questionId);

        long answeredCount = dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
                        session.getDailyQuizSessionId(),
                        DailyQuizAttemptType.ORIGINAL
                );

        return DailyQuizAnswerResponse.of(
                existingAttempt,
                correctOption.getOptionId(),
                existingAttempt.getSessionQuestion()
                        .getQuestion()
                        .getExplanation(),
                member.getCurrentStock(),
                answeredCount,
                QUESTIONS_PER_SESSION,
                session.getStatus()
        );
    }

    // 원본에서 틀린 문제 한번 더 제출
    private DailyQuizRetryAnswerResponse submitRetryAnswer(
            Long memberId,
            Long sessionId,
            Long sessionQuestionId,
            DailyQuizAnswerRequest request
    ) {
        DailyQuizSession session = findSessionForUpdate(sessionId, memberId);
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));

        // 만료 검사
        validateSessionNotExpired(session, now);

        // 세션 문제 확인
        DailyQuizSessionQuestion sessionQuestion = findSessionQuestion(
                sessionQuestionId,
                sessionId
        );

        // 기존 재풀이 답안 확인
        Optional<DailyQuizAttempt> existingRetryAttempt =
                dailyQuizAttemptRepository
                        .findBySessionQuestionSessionQuestionIdAndAttemptType(
                                sessionQuestionId,
                                DailyQuizAttemptType.RETRY
                        );

        if (existingRetryAttempt.isPresent()) {
            return handleExistingRetryAttempt(
                    existingRetryAttempt.get(),
                    request.selectedOptionId(),
                    session
            );
        }

        // 재풀이 가능한 세션 상태 확인
        if (session.getStatus() != DailyQuizSessionStatus.ORIGINAL_COMPLETED) {
            throw new ProjectException(DailyQuizErrorCode.RETRY_NOT_ALLOWED);
        }

        // 원본 답안 확인
        DailyQuizAttempt originalAttempt = dailyQuizAttemptRepository
                .findBySessionQuestionSessionQuestionIdAndAttemptType(
                        sessionQuestionId,
                        DailyQuizAttemptType.ORIGINAL
                )
                .orElseThrow(() ->
                        new ProjectException(DailyQuizErrorCode.RETRY_NOT_ALLOWED)
                );

        // 원본에서 틀린 문제인지 확인
        if (Boolean.TRUE.equals(originalAttempt.getCorrect())) {
            throw new ProjectException(DailyQuizErrorCode.RETRY_NOT_ALLOWED);
        }

        // 선택지 검증
        QuizOption selectedOption = findAndValidateSelectedOption(
                request.selectedOptionId(),
                sessionQuestion
        );

        // 재풀이 답안 저장
        DailyQuizAttempt retryAttempt = DailyQuizAttempt.create(
                sessionQuestion,
                selectedOption,
                DailyQuizAttemptType.RETRY,
                null,
                null,
                now
        );

        dailyQuizAttemptRepository.save(retryAttempt);

        // 재풀이 진행도 계산
        long retryRequiredCount = dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeAndCorrectFalse(
                        sessionId,
                        DailyQuizAttemptType.ORIGINAL
                );

        long retryCompletedCount = dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
                        sessionId,
                        DailyQuizAttemptType.RETRY
                );

        // 모든 오답 재풀이 완료 시 세션 완료
        if (retryCompletedCount == retryRequiredCount) {
            session.complete();
        }

        QuizOption correctOption = findCorrectOption(
                sessionQuestion.getQuestion().getQuestionId()
        );

        // 재풀이 결과 반환
        return DailyQuizRetryAnswerResponse.of(
                retryAttempt,
                correctOption.getOptionId(),
                sessionQuestion.getQuestion().getExplanation(),
                retryCompletedCount,
                retryRequiredCount,
                session.getStatus()
        );
    }

    // 같은 재풀이 요청이 중복으로 들어왔을 때
    private DailyQuizRetryAnswerResponse handleExistingRetryAttempt(
            DailyQuizAttempt existingAttempt,
            Long selectedOptionId,
            DailyQuizSession session
    ) {
        if (!Objects.equals(
                existingAttempt.getSelectedOption().getOptionId(),
                selectedOptionId
        )) {
            throw new ProjectException(
                    DailyQuizErrorCode.ANSWER_ALREADY_SUBMITTED
            );
        }

        Long questionId = existingAttempt
                .getSessionQuestion()
                .getQuestion()
                .getQuestionId();

        QuizOption correctOption = findCorrectOption(questionId);

        long retryRequiredCount = dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeAndCorrectFalse(
                        session.getDailyQuizSessionId(),
                        DailyQuizAttemptType.ORIGINAL
                );

        long retryCompletedCount = dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
                        session.getDailyQuizSessionId(),
                        DailyQuizAttemptType.RETRY
                );

        return DailyQuizRetryAnswerResponse.of(
                existingAttempt,
                correctOption.getOptionId(),
                existingAttempt.getSessionQuestion()
                        .getQuestion()
                        .getExplanation(),
                retryCompletedCount,
                retryRequiredCount,
                session.getStatus()
        );
    }

    private DailyQuizSession findSessionForUpdate(
            Long sessionId,
            Long memberId
    ) {
        return dailyQuizSessionRepository
                .findByIdAndMemberIdForUpdate(sessionId, memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                DailyQuizErrorCode.SESSION_NOT_FOUND
                        )
                );
    }

    private DailyQuizSessionQuestion findSessionQuestion(
            Long sessionQuestionId,
            Long sessionId
    ) {
        return sessionQuestionRepository
                .findBySessionQuestionIdAndDailyQuizSessionDailyQuizSessionId(
                        sessionQuestionId,
                        sessionId
                )
                .orElseThrow(() ->
                        new ProjectException(
                                DailyQuizErrorCode.SESSION_QUESTION_NOT_FOUND
                        )
                );
    }

    private QuizOption findAndValidateSelectedOption(
            Long selectedOptionId,
            DailyQuizSessionQuestion sessionQuestion
    ) {
        QuizOption selectedOption = quizOptionRepository
                .findById(selectedOptionId)
                .orElseThrow(() ->
                        new ProjectException(
                                DailyQuizErrorCode.OPTION_NOT_FOUND
                        )
                );

        if (!Objects.equals(
                selectedOption.getQuestion().getQuestionId(),
                sessionQuestion.getQuestion().getQuestionId()
        )) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_OPTION);
        }

        return selectedOption;
    }

    private QuizOption findCorrectOption(Long questionId) {
        return quizOptionRepository
                .findByQuestionQuestionIdAndCorrectTrue(questionId)
                .orElseThrow(() ->
                        new ProjectException(
                                DailyQuizErrorCode.CORRECT_OPTION_NOT_FOUND
                        )
                );
    }

    private void validateSessionNotExpired(
            DailyQuizSession session,
            LocalDateTime now
    ) {
        if (session.isExpired(now)) {
            throw new ProjectException(
                    DailyQuizErrorCode.SESSION_EXPIRED
            );
        }
    }
}
