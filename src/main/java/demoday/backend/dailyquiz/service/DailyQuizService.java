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
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerResult;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerResponse;
import demoday.backend.dailyquiz.dto.answer.DailyQuizRetryAnswerResponse;
import demoday.backend.dailyquiz.dto.category.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.dto.question.DailyQuizOptionResponse;
import demoday.backend.dailyquiz.dto.question.DailyQuizQuestionResponse;
import demoday.backend.dailyquiz.dto.result.DailyQuizResultQuestionResponse;
import demoday.backend.dailyquiz.dto.result.DailyQuizResultResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizActiveSessionResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionDetailResponse;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
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
import demoday.backend.quiz.domain.MemberQuestionHistory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.MemberQuestionHistoryRepository;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyQuizService {

    private static final int DAILY_QUIZ_QUESTION_COUNT = 5;
    private static final long DAILY_QUIZ_FISH_COST = 50L;
    private static final int NORMAL_STOCK_OPPORTUNITY_LIMIT = 10;
    private static final int PASS_STOCK_OPPORTUNITY_LIMIT = 15;
    private static final int MIN_STOCK_INCREASE_PERCENT = 1;
    private static final int MAX_STOCK_INCREASE_PERCENT = 10;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final EnumSet<DailyQuizSessionStatus> ACTIVE_STATUSES =
            EnumSet.of(
                    DailyQuizSessionStatus.IN_PROGRESS,
                    DailyQuizSessionStatus.ORIGINAL_COMPLETED
            );

    private final MemberRepository memberRepository;
    private final MemberPassRepository memberPassRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final DailyQuizSessionRepository dailyQuizSessionRepository;
    private final DailyQuizSessionQuestionRepository sessionQuestionRepository;
    private final FishService fishService;
    private final TransactionRetryExecutor transactionRetryExecutor;
    private final DailyQuizAttemptRepository dailyQuizAttemptRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final MemberDailyActivityRepository memberDailyActivityRepository;
    private final MemberQuestionHistoryRepository memberQuestionHistoryRepository;
    private final StockService stockService;

    @Transactional(readOnly = true)
    public List<DailyQuizCategoryResponse> getCategories() {
        return Arrays.stream(QuizCategory.values())
                .filter(category -> !category.isPreview())
                .map(DailyQuizCategoryResponse::from)
                .toList();
    }

    public DailyQuizSessionCreateResponse createSession(
            Long memberId,
            DailyQuizSessionCreateRequest request
    ) {
        validateDailyQuizCategory(request.category());

        return transactionRetryExecutor.execute(
                () -> createSessionInTransaction(memberId, request)
        );
    }

    private DailyQuizSessionCreateResponse createSessionInTransaction(
            Long memberId,
            DailyQuizSessionCreateRequest request
    ) {
        LocalDateTime now = LocalDateTime.now(KST);

        // 회원을 비관적 락으로 조회
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(GeneralErrorCode.NOT_FOUND)
                );

        // 기존 진행 중 세션 확인
        validateActiveSession(memberId, now);

        // 현재 활성화된 일주일 패스가 있는지 확인
        boolean passApplied = memberPassRepository.findActivePass(
                memberId,
                PassStatus.ACTIVE,
                now
        ).isPresent();

        // 없다면 생선 50개 보유 여부 확인
        if (!passApplied
                && member.getFishBalance() < DAILY_QUIZ_FISH_COST) {
            throw new ProjectException(
                    DailyQuizErrorCode.INSUFFICIENT_FISH
            );
        }

        // 선택한 카테고리에서 출제할 문제 조회
        List<QuizQuestion> questions =
                quizQuestionRepository.findAvailableQuestions(
                        memberId,
                        request.category(),
                        PageRequest.of(
                                0,
                                DAILY_QUIZ_QUESTION_COUNT
                        )
                );

        // 문제가 5개보다 적으면 INSUFFICIENT_QUESTIONS
        if (questions.size() < DAILY_QUIZ_QUESTION_COUNT) {
            throw new ProjectException(
                    DailyQuizErrorCode.INSUFFICIENT_QUESTIONS
            );
        }

        // 세션 생성
        DailyQuizSession session = DailyQuizSession.create(
                member,
                request.category(),
                member.getCurrentStock(),
                now,
                passApplied
        );

        dailyQuizSessionRepository.save(session);

        // 선정한 문제 5개 저장
        List<DailyQuizSessionQuestion> sessionQuestions =
                questions.stream()
                        .map(question ->
                                DailyQuizSessionQuestion.create(
                                        question,
                                        session
                                )
                        )
                        .toList();

        sessionQuestionRepository.saveAll(sessionQuestions);

        // 패스가 없다면 생선 50개 차감 후 기록
        if (!passApplied) {
            fishService.debit(
                    memberId,
                    DAILY_QUIZ_FISH_COST,
                    FishTransactionType.DAILY_QUIZ_COST,
                    session.getDailyQuizSessionId(),
                    "DAILY_QUIZ_SESSION:" + session.getDailyQuizSessionId()
            );
        }

        // 생성된 세션 정보 응답
        return DailyQuizSessionCreateResponse.from(session);
    }

    private void validateActiveSession(
            Long memberId,
            LocalDateTime now
    ) {
        dailyQuizSessionRepository
                .findFirstByMemberMemberIdAndStatusInOrderByStartedAtDesc(
                        memberId,
                        ACTIVE_STATUSES
                )
                .filter(session -> !session.isExpired(now))
                .ifPresent(session -> {
                    throw new ProjectException(
                            DailyQuizErrorCode.ACTIVE_SESSION_ALREADY_EXISTS
                    );
                });
    }

    // 사용자가 이어서 풀 수 있는 세션 있는지 조회
    @Transactional(readOnly = true)
    public DailyQuizActiveSessionResponse getActiveSession(
            Long memberId
    ) {
        // 현재 KST 시각 조회
        LocalDateTime now = LocalDateTime.now(KST);

        DailyQuizSession session =
                // 진행 상태인 가장 최근 세션 조회
                dailyQuizSessionRepository
                        .findFirstByMemberMemberIdAndStatusInOrderByStartedAtDesc(
                                memberId,
                                ACTIVE_STATUSES
                        )
                        // 실제 만료 시각 확인
                        .filter(activeSession ->
                                !activeSession.isExpired(now)
                        )
                        // 진행 중 세션 없을 시 예외 발생
                        .orElseThrow(() ->
                                new ProjectException(
                                        DailyQuizErrorCode.SESSION_NOT_FOUND
                                )
                        );

        // 원본 답안 제출 개수 조회
        long answeredCount =
                dailyQuizAttemptRepository
                        .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
                                session.getDailyQuizSessionId(),
                                DailyQuizAttemptType.ORIGINAL
                        );

        // 진행 중 세션 요약 반환
        return DailyQuizActiveSessionResponse.of(
                session,
                answeredCount,
                DAILY_QUIZ_QUESTION_COUNT
        );
    }

    // 최초 진입 또는 이어 풀기 화면 복원
    @Transactional(readOnly = true)
    public DailyQuizSessionDetailResponse getSessionDetail(
            Long memberId,
            Long sessionId
    ) {
        // 현재 KST 시각 조회
        LocalDateTime now = LocalDateTime.now(KST);

        // 본인 소유 세션 조회
        DailyQuizSession session =
                dailyQuizSessionRepository
                        .findByDailyQuizSessionIdAndMemberMemberId(
                                sessionId,
                                memberId
                        )
                        .orElseThrow(() ->
                                new ProjectException(
                                        DailyQuizErrorCode.SESSION_NOT_FOUND
                                )
                        );

        // 세션 만료 검사
        validateSessionNotExpired(session, now);

        // 세션에 고정된 문제 5개 조회
        List<DailyQuizSessionQuestion> sessionQuestions =
                sessionQuestionRepository
                        .findAllByDailyQuizSessionDailyQuizSessionIdOrderByQuestionQuestionIdAsc(
                                sessionId
                        );

        // 문제 5개 존재 여부 확인
        if (sessionQuestions.size()
                != DAILY_QUIZ_QUESTION_COUNT) {
            throw new ProjectException(
                    DailyQuizErrorCode.INVALID_SESSION_QUESTION_COUNT
            );
        }

        // 제출한 원본 답안 조회
        List<DailyQuizAttempt> originalAttempts =
                dailyQuizAttemptRepository
                        .findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
                                sessionId,
                                DailyQuizAttemptType.ORIGINAL
                        );

        // 재풀이 답안 조회
        List<DailyQuizAttempt> retryAttempts =
                dailyQuizAttemptRepository
                        .findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
                                sessionId,
                                DailyQuizAttemptType.RETRY
                        );

        // 문제 ID 목록 추출
        List<Long> questionIds =
                sessionQuestions.stream()
                        .map(sessionQuestion ->
                                sessionQuestion.getQuestion()
                                        .getQuestionId()
                        )
                        .toList();

        // 선택지 일괄 조회
        List<QuizOption> options =
                quizOptionRepository
                        .findAllByQuestionQuestionIdInOrderByQuestionQuestionIdAscOptionNumberAsc(
                                questionIds
                        );

        // 문제별 선택지 그룹화
        Map<Long, List<DailyQuizOptionResponse>> optionResponsesByQuestionId =
                options.stream()
                        .collect(
                                Collectors.groupingBy(
                                        option ->
                                                option.getQuestion()
                                                        .getQuestionId(),
                                        Collectors.mapping(
                                                DailyQuizOptionResponse::from,
                                                Collectors.toList()
                                        )
                                )
                        );

        // 세션 문제별 원본 답안 매핑
        Map<Long, DailyQuizAttempt> originalAttemptBySessionQuestionId =
                originalAttempts.stream()
                        .collect(
                                Collectors.toMap(
                                        attempt ->
                                                attempt.getSessionQuestion()
                                                        .getSessionQuestionId(),
                                        attempt -> attempt
                                )
                        );

        // 재풀이를 완료한 세션 문제 ID 수집
        Set<Long> retryAnsweredSessionQuestionIds =
                retryAttempts.stream()
                        .map(attempt ->
                                attempt.getSessionQuestion()
                                        .getSessionQuestionId()
                        )
                        .collect(Collectors.toSet());

        // 문제 응답 생성
        List<DailyQuizQuestionResponse> questionResponses =
                sessionQuestions.stream()
                        .map(sessionQuestion -> {
                            Long sessionQuestionId =
                                    sessionQuestion.getSessionQuestionId();

                            Long questionId =
                                    sessionQuestion.getQuestion()
                                            .getQuestionId();

                            DailyQuizAttempt originalAttempt =
                                    originalAttemptBySessionQuestionId.get(
                                            sessionQuestionId
                                    );

                            boolean originalAnswered =
                                    originalAttempt != null;

                            Boolean originalCorrect =
                                    originalAnswered
                                            ? originalAttempt.getCorrect()
                                            : null;

                            boolean retryAnswered =
                                    retryAnsweredSessionQuestionIds.contains(
                                            sessionQuestionId
                                    );

                            return DailyQuizQuestionResponse.of(
                                    sessionQuestion,
                                    originalAnswered,
                                    originalCorrect,
                                    retryAnswered,
                                    optionResponsesByQuestionId
                                            .getOrDefault(
                                                    questionId,
                                                    List.of()
                                            )
                            );
                        })
                        .toList();

        // 상세 응답 반환
        return DailyQuizSessionDetailResponse.of(
                session,
                originalAttempts.size(),
                questionResponses
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
        // 현재 KST 시각 확인
        LocalDateTime now = LocalDateTime.now(KST);

        // 회원 비관적 락 조회
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(GeneralErrorCode.NOT_FOUND)
                );

        DailyQuizSession session = findSessionForUpdate(sessionId, memberId);

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
                    session
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
        int stockOpportunityLimit = Boolean.TRUE.equals(session.getPassApplied())
                ? PASS_STOCK_OPPORTUNITY_LIMIT
                : NORMAL_STOCK_OPPORTUNITY_LIMIT;

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
        if (answeredCount == DAILY_QUIZ_QUESTION_COUNT) {
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

                member.completeLearning(learnedYesterday);
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
                DAILY_QUIZ_QUESTION_COUNT,
                session.getStatus()
        );
    }

    // 같은 답안 제출 요청 중복으로 들어왔을 때 처리 (네트워크 재시도, 사용자의 연속 클릭 등..)
    private DailyQuizAnswerResponse handleExistingOriginalAttempt(
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
                existingAttempt.getStockAfter(),
                answeredCount,
                DAILY_QUIZ_QUESTION_COUNT,
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
        LocalDateTime now = LocalDateTime.now(KST);

        DailyQuizSession session = findSessionForUpdate(sessionId, memberId);

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

    // 원본 풀이 결과 팝업과 최종 총정리 화면에 필요한 데이터 조회
    @Transactional(readOnly = true)
    public DailyQuizResultResponse getResult(
            Long memberId,
            Long sessionId
    ) {

        // 본인 세션 조회
        DailyQuizSession session = dailyQuizSessionRepository
                .findByDailyQuizSessionIdAndMemberMemberId(sessionId, memberId)
                .orElseThrow(() ->
                        new ProjectException(DailyQuizErrorCode.SESSION_NOT_FOUND)
                );

        // 세션 상태 확인
        if (session.getStatus() != DailyQuizSessionStatus.ORIGINAL_COMPLETED
                && session.getStatus() != DailyQuizSessionStatus.COMPLETED) {
            throw new ProjectException(
                    DailyQuizErrorCode.INVALID_SESSION_STATE
            );
        }

        // 원본 답안 5개 확인
        List<DailyQuizAttempt> originalAttempts = dailyQuizAttemptRepository
                .findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
                        sessionId,
                        DailyQuizAttemptType.ORIGINAL
                );

        if (originalAttempts.size() != DAILY_QUIZ_QUESTION_COUNT) {
            throw new ProjectException(
                    DailyQuizErrorCode.INVALID_SESSION_QUESTION_COUNT
            );
        }

        // 재풀이 답안 조회
        List<DailyQuizAttempt> retryAttempts = dailyQuizAttemptRepository
                .findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
                        sessionId,
                        DailyQuizAttemptType.RETRY
                );

        Map<Long, DailyQuizAttempt> retryAttemptBySessionQuestionId =
                retryAttempts.stream()
                        .collect(Collectors.toMap(
                                attempt -> attempt.getSessionQuestion()
                                        .getSessionQuestionId(),
                                attempt -> attempt
                        ));

        List<Long> questionIds = originalAttempts.stream()
                .map(attempt -> attempt.getSessionQuestion()
                        .getQuestion()
                        .getQuestionId())
                .toList();

        // 정답 선택지 일괄 조회
        Map<Long, QuizOption> correctOptionByQuestionId =
                quizOptionRepository
                        .findAllByQuestionQuestionIdInOrderByQuestionQuestionIdAscOptionNumberAsc(
                                questionIds
                        )
                        .stream()
                        .filter(option ->
                                Boolean.TRUE.equals(
                                        option.getCorrect()
                                )
                        )
                        .collect(
                                Collectors.toMap(
                                        option ->
                                                option.getQuestion()
                                                        .getQuestionId(),
                                        option -> option
                                )
                        );

        if (correctOptionByQuestionId.size()
                != DAILY_QUIZ_QUESTION_COUNT) {
            throw new ProjectException(
                    DailyQuizErrorCode.CORRECT_OPTION_NOT_FOUND
            );
        }

        // 문제별 주가 변화 및 결과 생성
        List<DailyQuizResultQuestionResponse> questionResponses =
                new ArrayList<>();

        for (DailyQuizAttempt originalAttempt : originalAttempts) {
            Long sessionQuestionId = originalAttempt
                    .getSessionQuestion()
                    .getSessionQuestionId();

            Long questionId = originalAttempt
                    .getSessionQuestion()
                    .getQuestion()
                    .getQuestionId();

            questionResponses.add(
                    DailyQuizResultQuestionResponse.of(
                            originalAttempt,
                            correctOptionByQuestionId.get(questionId),
                            retryAttemptBySessionQuestionId.get(
                                    sessionQuestionId
                            ),
                            originalAttempt.getStockAfter()
                    )
            );
        }

        // 정답 및 오답 개수 계산
        int correctCount = (int) originalAttempts.stream()
                .filter(attempt -> Boolean.TRUE.equals(attempt.getCorrect()))
                .count();

        int incorrectCount = DAILY_QUIZ_QUESTION_COUNT - correctCount;

        BigDecimal endStock = session.getEndStock();

        // 주가 결과 계산
        BigDecimal totalProfit = endStock
                .subtract(session.getStartStock())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal totalReturnPercent = totalProfit
                .divide(session.getStartStock(), 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);

        return DailyQuizResultResponse.of(
                session,
                correctCount,
                incorrectCount,
                retryAttempts.size(),
                endStock,
                totalProfit,
                totalReturnPercent,
                questionResponses
        );
    }

    // 사용자가 재풀이하지 않고 NEXT 눌렀을 때
    @Transactional
    public DailyQuizResultResponse completeSession(
            Long memberId,
            Long sessionId
    ) {
        LocalDateTime now = LocalDateTime.now(KST);

        // 세션 락 조회
        DailyQuizSession session = findSessionForUpdate(sessionId, memberId);

        // 이미 완료된 세션 처리
        if (session.getStatus() == DailyQuizSessionStatus.COMPLETED) {
            return getResult(memberId, sessionId);
        }

        // 만료 검사
        validateSessionNotExpired(session, now);

        // 원본 완료 상태 확인
        if (session.getStatus() != DailyQuizSessionStatus.ORIGINAL_COMPLETED) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_SESSION_STATE);
        }

        // 종료 주가 확정
        session.complete();

        // 최종 결과 반환
        return getResult(memberId, sessionId);
    }

    // 퀴즈 카테고리 검증 메서드
    private void validateDailyQuizCategory(QuizCategory category) {
        if (category.isPreview()) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_CATEGORY);
        }
    }
}
