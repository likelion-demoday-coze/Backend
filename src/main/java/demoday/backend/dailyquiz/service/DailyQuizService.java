package demoday.backend.dailyquiz.service;

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
import demoday.backend.dailyquiz.dto.category.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.dto.question.DailyQuizOptionResponse;
import demoday.backend.dailyquiz.dto.question.DailyQuizQuestionResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizActiveSessionResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionDetailResponse;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.domain.FishTransaction;
import demoday.backend.fish.repository.FishTransactionRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final FishTransactionRepository fishTransactionRepository;
    private final DailyQuizAttemptRepository dailyQuizAttemptRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final MemberDailyActivityRepository memberDailyActivityRepository;
    private final MemberQuestionHistoryRepository memberQuestionHistoryRepository;

    @Transactional(readOnly = true)
    public List<DailyQuizCategoryResponse> getCategories() {
        return Arrays.stream(QuizCategory.values())
                .map(DailyQuizCategoryResponse::from)
                .toList();
    }

    @Transactional
    public DailyQuizSessionCreateResponse createSession(
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
            deductFishAndRecordTransaction(
                    member,
                    session,
                    now
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

    private void deductFishAndRecordTransaction(
            Member member,
            DailyQuizSession session,
            LocalDateTime now
    ) {
        member.deductFish(DAILY_QUIZ_FISH_COST);

        FishTransaction transaction =
                FishTransaction.create(
                        member,
                        FishTransactionType.DAILY_QUIZ_COST,
                        -DAILY_QUIZ_FISH_COST,
                        member.getFishBalance(),
                        session.getDailyQuizSessionId(),
                        "DAILY_QUIZ_SESSION:"
                                + session.getDailyQuizSessionId(),
                        now
                );

        fishTransactionRepository.save(transaction);
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

    // 원본 문제 답안을 최초 제출
    @Transactional
    public DailyQuizAnswerResponse submitOriginalAnswer(
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

        // 세션 비관적 락 조회
        DailyQuizSession session = dailyQuizSessionRepository
                .findByIdAndMemberIdForUpdate(sessionId, memberId)
                .orElseThrow(() ->
                        new ProjectException(DailyQuizErrorCode.SESSION_NOT_FOUND)
                );

        // 세션 만료 확인
        validateSessionNotExpired(session, now);

        // 세션 문제 조회
        DailyQuizSessionQuestion sessionQuestion =
                sessionQuestionRepository
                        .findBySessionQuestionIdAndDailyQuizSessionDailyQuizSessionId(
                                sessionQuestionId,
                                sessionId
                        )
                        .orElseThrow(() ->
                                new ProjectException(
                                        DailyQuizErrorCode.SESSION_QUESTION_NOT_FOUND
                                )
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
                    member,
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
        QuizOption selectedOption = quizOptionRepository
                .findById(request.selectedOptionId())
                .orElseThrow(() ->
                        new ProjectException(DailyQuizErrorCode.OPTION_NOT_FOUND)
                );

        // 선택지 소속 문제 검증
        if (!Objects.equals(
                selectedOption.getQuestion().getQuestionId(),
                sessionQuestion.getQuestion().getQuestionId()
        )) {
            throw new ProjectException(DailyQuizErrorCode.INVALID_OPTION);
        }

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

        // 정답이면 주가 상승
        if (Boolean.TRUE.equals(selectedOption.getCorrect())
                && stockOpportunityAvailable) {
            stockIncreasePercent = ThreadLocalRandom.current().nextInt(
                    MIN_STOCK_INCREASE_PERCENT,
                    MAX_STOCK_INCREASE_PERCENT + 1
            );

            member.increaseStock(stockIncreasePercent);
        }

        // 원본 답안 저장
        DailyQuizAttempt attempt = DailyQuizAttempt.create(
                sessionQuestion,
                selectedOption,
                DailyQuizAttemptType.ORIGINAL,
                stockIncreasePercent,
                now
        );

        dailyQuizAttemptRepository.save(attempt);

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

        // 5문제 완료 처리
        if (answeredCount == DAILY_QUIZ_QUESTION_COUNT) {
            session.completeOriginal();
            dailyActivity.completeLearning(now);
        }

        // 정답 선택지 조회
        QuizOption correctOption = quizOptionRepository
                .findByQuestionQuestionIdAndCorrectTrue(
                        sessionQuestion.getQuestion().getQuestionId()
                )
                .orElseThrow(() ->
                        new ProjectException(DailyQuizErrorCode.CORRECT_OPTION_NOT_FOUND)
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
            Member member,
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

        QuizOption correctOption = quizOptionRepository
                .findByQuestionQuestionIdAndCorrectTrue(questionId)
                .orElseThrow(() ->
                        new ProjectException(DailyQuizErrorCode.CORRECT_OPTION_NOT_FOUND)
                );

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
                DAILY_QUIZ_QUESTION_COUNT,
                session.getStatus()
        );
    }
}
