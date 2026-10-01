package demoday.backend.timeattack.service;

import demoday.backend.activity.code.LearningStatus;
import demoday.backend.activity.domain.MemberDailyActivity;
import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
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
import demoday.backend.timeattack.dto.answer.TimeAttackAnswerRequest;
import demoday.backend.timeattack.dto.answer.TimeAttackAnswerResponse;
import demoday.backend.timeattack.dto.question.TimeAttackQuestionResponse;
import demoday.backend.timeattack.dto.result.TimeAttackResultResponse;
import demoday.backend.timeattack.dto.session.TimeAttackCompleteResponse;
import demoday.backend.timeattack.dto.session.TimeAttackSessionCreateResponse;
import demoday.backend.timeattack.dto.session.TimeAttackTodayResponse;
import demoday.backend.timeattack.repository.TimeAttackAnswerRepository;
import demoday.backend.timeattack.repository.TimeAttackSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TimeAttackServiceTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Long MEMBER_ID = 1L;
    private static final Long SESSION_ID = 10L;

    @Mock MemberRepository memberRepository;
    @Mock MemberDailyActivityRepository memberDailyActivityRepository;
    @Mock MemberPassRepository memberPassRepository;
    @Mock TimeAttackSessionRepository timeAttackSessionRepository;
    @Mock FishService fishService;
    @Mock TransactionRetryExecutor transactionRetryExecutor;
    @Mock QuizQuestionRepository quizQuestionRepository;
    @Mock QuizOptionRepository quizOptionRepository;
    @Mock TimeAttackAnswerRepository timeAttackAnswerRepository;

    @InjectMocks TimeAttackService timeAttackService;

    @BeforeEach
    void setUpRetryExecutor() {
        lenient().when(transactionRetryExecutor.execute(any()))
                .thenAnswer(invocation -> {
                    Supplier<?> operation = invocation.getArgument(0);
                    return operation.get();
                });
    }

    @Test
    @DisplayName("오늘 사용한 횟수로 남은 참여 횟수를 계산한다")
    void getTodayAvailability() {
        MemberDailyActivity activity = activity(member(), LocalDate.now(KST));
        activity.recordTimeAttackAttempt(3);
        when(memberDailyActivityRepository.findByMemberMemberIdAndActivityDate(
                eq(MEMBER_ID), any(LocalDate.class)
        )).thenReturn(Optional.of(activity));

        TimeAttackTodayResponse result = timeAttackService.getTodayAvailability(MEMBER_ID);

        assertThat(result.dailyAttemptLimit()).isEqualTo(3);
        assertThat(result.usedAttemptCount()).isEqualTo(1);
        assertThat(result.remainingAttemptCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("패스가 없으면 생선 100개를 차감하고 세션을 생성한다")
    void createSessionWithoutPass() {
        Member member = member();
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberDailyActivityRepository.findByMemberMemberIdAndActivityDate(
                eq(MEMBER_ID), any(LocalDate.class)
        )).thenReturn(Optional.empty());
        when(memberPassRepository.findActivePass(
                eq(MEMBER_ID), eq(PassStatus.ACTIVE), any(LocalDateTime.class)
        )).thenReturn(Optional.empty());
        when(timeAttackSessionRepository.save(any(TimeAttackSession.class)))
                .thenAnswer(invocation -> {
                    TimeAttackSession session = invocation.getArgument(0);
                    ReflectionTestUtils.setField(session, "timeAttackSessionId", SESSION_ID);
                    return session;
                });

        TimeAttackSessionCreateResponse result = timeAttackService.createSession(MEMBER_ID);

        assertThat(result.sessionId()).isEqualTo(SESSION_ID);
        assertThat(result.status()).isEqualTo(TimeAttackStatus.IN_PROGRESS);
        assertThat(result.passApplied()).isFalse();
        verify(fishService).debit(
                MEMBER_ID, 100L, FishTransactionType.TIME_ATTACK_COST,
                SESSION_ID, "TIME_ATTACK_SESSION:" + SESSION_ID
        );
        verify(memberDailyActivityRepository).save(argThat(
                saved -> saved.getTimeAttackAttemptCount() == 1
        ));
    }

    @Test
    @DisplayName("일일 참여 횟수를 모두 사용하면 세션을 생성할 수 없다")
    void createSessionFailsWhenDailyLimitExceeded() {
        Member member = member();
        MemberDailyActivity activity = activity(member, LocalDate.now(KST));
        activity.recordTimeAttackAttempt(3);
        activity.recordTimeAttackAttempt(3);
        activity.recordTimeAttackAttempt(3);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(memberDailyActivityRepository.findByMemberMemberIdAndActivityDate(
                eq(MEMBER_ID), any(LocalDate.class)
        )).thenReturn(Optional.of(activity));

        assertError(
                () -> timeAttackService.createSession(MEMBER_ID),
                TimeAttackErrorCode.DAILY_ATTEMPT_LIMIT_EXCEEDED
        );
        verifyNoInteractions(memberPassRepository, fishService);
        verify(timeAttackSessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("다음 문제와 현재 연속 정답 수를 조회한다")
    void getNextQuestion() {
        TimeAttackSession session = session(member(), LocalDateTime.now(KST).minusSeconds(10));
        QuizQuestion question = question(100L, "문제", "해설");
        List<QuizOption> options = List.of(
                option(1001L, question, 1, "오답", false),
                option(1002L, question, 2, "정답", true)
        );
        when(timeAttackSessionRepository
                .findByTimeAttackSessionIdAndMemberMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(quizQuestionRepository.findNextTimeAttackQuestion(
                SESSION_ID, QuizCategory.PREVIEW, PageRequest.of(0, 1)
        )).thenReturn(List.of(question));
        when(quizOptionRepository.findAllByQuestionQuestionIdOrderByOptionNumberAsc(100L))
                .thenReturn(options);
        when(timeAttackAnswerRepository.findCorrectResultsBySessionIdOrderByLatest(SESSION_ID))
                .thenReturn(List.of(true, true, false));

        TimeAttackQuestionResponse result =
                timeAttackService.getNextQuestion(MEMBER_ID, SESSION_ID);

        assertThat(result.question().questionId()).isEqualTo(100L);
        assertThat(result.question().options()).hasSize(2);
        assertThat(result.consecutiveCorrectCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("제한 시간이 지나면 다음 문제를 조회할 수 없다")
    void getNextQuestionFailsWhenExpired() {
        TimeAttackSession session = session(member(), LocalDateTime.now(KST).minusSeconds(61));
        when(timeAttackSessionRepository
                .findByTimeAttackSessionIdAndMemberMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        assertError(
                () -> timeAttackService.getNextQuestion(MEMBER_ID, SESSION_ID),
                TimeAttackErrorCode.SESSION_EXPIRED
        );
        verifyNoInteractions(quizQuestionRepository, quizOptionRepository);
    }

    @Test
    @DisplayName("정답 제출 시 답안을 저장하고 누적 정답 수와 콤보를 반환한다")
    void submitCorrectAnswer() {
        TimeAttackSession session = session(member(), LocalDateTime.now(KST).minusSeconds(10));
        QuizQuestion question = question(100L, "문제", "해설");
        QuizOption correctOption = option(1002L, question, 2, "정답", true);
        when(timeAttackSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(timeAttackAnswerRepository
                .existsByTimeAttackSessionTimeAttackSessionIdAndQuestionQuestionId(
                        SESSION_ID, 100L
                )).thenReturn(false);
        when(quizQuestionRepository.findNextTimeAttackQuestion(
                SESSION_ID, QuizCategory.PREVIEW, PageRequest.of(0, 1)
        )).thenReturn(List.of(question));
        when(quizOptionRepository.findByOptionIdAndQuestionQuestionId(1002L, 100L))
                .thenReturn(Optional.of(correctOption));
        when(quizOptionRepository.findByQuestionQuestionIdAndCorrectTrue(100L))
                .thenReturn(Optional.of(correctOption));
        when(timeAttackAnswerRepository.findCorrectResultsBySessionIdOrderByLatest(SESSION_ID))
                .thenReturn(List.of(true, true));

        TimeAttackAnswerResponse result = timeAttackService.submitAnswer(
                MEMBER_ID, SESSION_ID, new TimeAttackAnswerRequest(100L, 1002L)
        );

        assertThat(result.correct()).isTrue();
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.consecutiveCorrectCount()).isEqualTo(2);
        verify(timeAttackAnswerRepository).save(any(TimeAttackAnswer.class));
    }

    @Test
    @DisplayName("현재 문제가 아니면 답안을 제출할 수 없다")
    void submitAnswerFailsWhenQuestionIsNotCurrent() {
        TimeAttackSession session = session(member(), LocalDateTime.now(KST).minusSeconds(10));
        QuizQuestion current = question(100L, "현재 문제", "해설");
        when(timeAttackSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(timeAttackAnswerRepository
                .existsByTimeAttackSessionTimeAttackSessionIdAndQuestionQuestionId(
                        SESSION_ID, 200L
                )).thenReturn(false);
        when(quizQuestionRepository.findNextTimeAttackQuestion(
                SESSION_ID, QuizCategory.PREVIEW, PageRequest.of(0, 1)
        )).thenReturn(List.of(current));

        assertError(
                () -> timeAttackService.submitAnswer(
                        MEMBER_ID, SESSION_ID, new TimeAttackAnswerRequest(200L, 2001L)
                ),
                TimeAttackErrorCode.QUESTION_NOT_CURRENT
        );
        verifyNoInteractions(quizOptionRepository);
        verify(timeAttackAnswerRepository, never()).save(any());
    }

    @Test
    @DisplayName("60초 전에는 정상 종료할 수 없다")
    void completeSessionFailsBeforeTimeLimit() {
        Member member = member();
        TimeAttackSession session = session(member, LocalDateTime.now(KST).minusSeconds(10));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(timeAttackSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(timeAttackAnswerRepository.findCorrectResultsBySessionIdOrderByLatest(SESSION_ID))
                .thenReturn(List.of());

        assertError(
                () -> timeAttackService.completeSession(MEMBER_ID, SESSION_ID),
                TimeAttackErrorCode.TOO_EARLY_TO_COMPLETE
        );
        verify(memberDailyActivityRepository, never()).save(any());
    }

    @Test
    @DisplayName("완료 유예시간이 지나면 세션을 만료 처리한다")
    void completeSessionExpiresAfterGracePeriod() {
        Member member = member();
        TimeAttackSession session = session(
                member,
                LocalDateTime.now(KST).minusSeconds(71)
        );
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(timeAttackSessionRepository.findByIdAndMemberIdForUpdate(
                SESSION_ID, MEMBER_ID
        )).thenReturn(Optional.of(session));
        when(timeAttackAnswerRepository.findCorrectResultsBySessionIdOrderByLatest(
                SESSION_ID
        )).thenReturn(List.of(true));

        assertError(
                () -> timeAttackService.completeSession(MEMBER_ID, SESSION_ID),
                TimeAttackErrorCode.SESSION_COMPLETION_EXPIRED
        );

        assertThat(session.getStatus()).isEqualTo(TimeAttackStatus.EXPIRED);
        verify(memberDailyActivityRepository, never())
                .findByMemberMemberIdAndActivityDate(anyLong(), any(LocalDate.class));
        verify(memberDailyActivityRepository, never()).save(any());
    }

    @Test
    @DisplayName("답안을 제출하지 않은 세션은 완료해도 학습 기록에 반영하지 않는다")
    void completeSessionWithoutAnswersDoesNotCompleteLearning() {
        Member member = member();
        TimeAttackSession session = session(
                member,
                LocalDateTime.now(KST).minusSeconds(61)
        );
        when(memberRepository.findByIdForUpdate(MEMBER_ID))
                .thenReturn(Optional.of(member));
        when(timeAttackSessionRepository.findByIdAndMemberIdForUpdate(
                SESSION_ID, MEMBER_ID
        )).thenReturn(Optional.of(session));
        when(timeAttackAnswerRepository.findCorrectResultsBySessionIdOrderByLatest(
                SESSION_ID
        )).thenReturn(List.of());

        TimeAttackCompleteResponse result =
                timeAttackService.completeSession(MEMBER_ID, SESSION_ID);

        assertThat(result.status()).isEqualTo(TimeAttackStatus.COMPLETED);
        assertThat(result.totalAnsweredCount()).isZero();
        assertThat(member.getCurrentStreak()).isZero();
        verify(memberDailyActivityRepository, never())
                .findByMemberMemberIdAndActivityDate(anyLong(), any(LocalDate.class));
        verify(memberDailyActivityRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 완료 기한이 다음 날이면 세션을 시작할 수 없다")
    void cannotStartWhenCompletionDeadlineCrossesMidnight() {
        LocalDateTime beforeMidnight =
                LocalDate.of(2026, 10, 1).atTime(23, 59);
        LocalDateTime safeStart =
                LocalDate.of(2026, 10, 1).atTime(23, 58, 49);

        assertThat(TimeAttackSession.canCompleteOnSameDate(beforeMidnight))
                .isFalse();
        assertThat(TimeAttackSession.canCompleteOnSameDate(safeStart))
                .isTrue();
    }

    @Test
    @DisplayName("정상 종료하면 학습 완료와 최대 콤보를 반영한다")
    void completeSession() {
        Member member = member();
        MemberDailyActivity activity = activity(member, LocalDate.now(KST));
        TimeAttackSession session = session(member, LocalDateTime.now(KST).minusSeconds(61));
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));
        when(timeAttackSessionRepository.findByIdAndMemberIdForUpdate(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(timeAttackAnswerRepository.findCorrectResultsBySessionIdOrderByLatest(SESSION_ID))
                .thenReturn(List.of(true, true, false, true));
        when(memberDailyActivityRepository.findByMemberMemberIdAndActivityDate(
                eq(MEMBER_ID), any(LocalDate.class)
        )).thenReturn(Optional.of(activity));
        when(memberDailyActivityRepository
                .existsByMemberMemberIdAndActivityDateAndLearningStatusIn(
                        eq(MEMBER_ID), any(LocalDate.class), anyCollection()
                )).thenReturn(true);

        TimeAttackCompleteResponse result =
                timeAttackService.completeSession(MEMBER_ID, SESSION_ID);

        assertThat(result.status()).isEqualTo(TimeAttackStatus.COMPLETED);
        assertThat(result.totalAnsweredCount()).isEqualTo(4);
        assertThat(result.maxConsecutiveCorrectCount()).isEqualTo(2);
        assertThat(activity.getLearningStatus()).isEqualTo(LearningStatus.COMPLETED);
        assertThat(member.getCurrentStreak()).isEqualTo(1);
    }

    @Test
    @DisplayName("정상 완료된 세션의 문제별 답안과 최대 콤보를 조회한다")
    void getResult() {
        Member member = member();
        TimeAttackSession session = session(member, LocalDateTime.now(KST).minusSeconds(61));
        QuizQuestion firstQuestion = question(100L, "첫 번째 문제", "첫 번째 해설");
        QuizQuestion secondQuestion = question(200L, "두 번째 문제", "두 번째 해설");
        QuizOption firstCorrect = option(1002L, firstQuestion, 2, "첫 번째 정답", true);
        QuizOption secondWrong = option(2001L, secondQuestion, 1, "두 번째 오답", false);
        QuizOption secondCorrect = option(2002L, secondQuestion, 2, "두 번째 정답", true);
        TimeAttackAnswer firstAnswer = TimeAttackAnswer.create(
                session, firstQuestion, firstCorrect, LocalDateTime.now(KST).minusSeconds(50)
        );
        TimeAttackAnswer secondAnswer = TimeAttackAnswer.create(
                session, secondQuestion, secondWrong, LocalDateTime.now(KST).minusSeconds(40)
        );
        session.increaseCorrectCount();
        session.complete(LocalDateTime.now(KST));

        when(timeAttackSessionRepository
                .findByTimeAttackSessionIdAndMemberMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));
        when(timeAttackAnswerRepository
                .findAllByTimeAttackSessionTimeAttackSessionIdOrderByAnsweredAtAscTimeAttackAnswerIdAsc(
                        SESSION_ID
                )).thenReturn(List.of(firstAnswer, secondAnswer));
        when(quizOptionRepository.findAllByQuestionQuestionIdInAndCorrectTrue(
                List.of(100L, 200L)
        )).thenReturn(List.of(firstCorrect, secondCorrect));

        TimeAttackResultResponse result =
                timeAttackService.getResult(MEMBER_ID, SESSION_ID);

        assertThat(result.totalAnsweredCount()).isEqualTo(2);
        assertThat(result.correctCount()).isEqualTo(1);
        assertThat(result.incorrectCount()).isEqualTo(1);
        assertThat(result.maxConsecutiveCorrectCount()).isEqualTo(1);
        assertThat(result.questions()).hasSize(2);
        assertThat(result.questions().get(1).selectedOptionContent()).isEqualTo("두 번째 오답");
        assertThat(result.questions().get(1).correctOptionContent()).isEqualTo("두 번째 정답");
        assertThat(result.questions().get(1).explanation()).isEqualTo("두 번째 해설");
    }

    @Test
    @DisplayName("정상 완료되지 않은 세션은 결과를 조회할 수 없다")
    void getResultFailsWhenNotCompleted() {
        TimeAttackSession session = session(member(), LocalDateTime.now(KST).minusSeconds(10));
        when(timeAttackSessionRepository
                .findByTimeAttackSessionIdAndMemberMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(session));

        assertError(
                () -> timeAttackService.getResult(MEMBER_ID, SESSION_ID),
                TimeAttackErrorCode.RESULT_NOT_AVAILABLE
        );
        verifyNoInteractions(timeAttackAnswerRepository);
    }

    private Member member() {
        Member member = Member.create(12345L, "테스터");
        ReflectionTestUtils.setField(member, "memberId", MEMBER_ID);
        return member;
    }

    private MemberDailyActivity activity(Member member, LocalDate date) {
        return MemberDailyActivity.create(member, date);
    }

    private TimeAttackSession session(Member member, LocalDateTime startedAt) {
        TimeAttackSession session = TimeAttackSession.create(
                member, startedAt.toLocalDate(), startedAt, false
        );
        ReflectionTestUtils.setField(session, "timeAttackSessionId", SESSION_ID);
        return session;
    }

    private QuizQuestion question(Long id, String content, String explanation) {
        QuizQuestion question = QuizQuestion.create(
                QuizCategory.STOCK_INVESTMENT,
                "MULTIPLE_CHOICE",
                content,
                explanation,
                true
        );
        ReflectionTestUtils.setField(question, "questionId", id);
        return question;
    }

    private QuizOption option(
            Long id, QuizQuestion question, int number,
            String content, boolean correct
    ) {
        QuizOption option = QuizOption.create(question, number, content, correct);
        ReflectionTestUtils.setField(option, "optionId", id);
        return option;
    }

    private void assertError(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable callable,
            TimeAttackErrorCode expectedCode
    ) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(expectedCode)
                );
    }
}
