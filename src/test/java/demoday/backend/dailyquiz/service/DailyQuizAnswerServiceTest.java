package demoday.backend.dailyquiz.service;

import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.domain.DailyQuizAttempt;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerResponse;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.MemberQuestionHistoryRepository;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.stock.service.StockService;
import demoday.backend.streak.service.StreakPenaltyService;
import demoday.backend.streak.service.StreakRecoveryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyQuizAnswerServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private DailyQuizSessionRepository dailyQuizSessionRepository;
    @Mock private DailyQuizSessionQuestionRepository sessionQuestionRepository;
    @Mock private DailyQuizAttemptRepository dailyQuizAttemptRepository;
    @Mock private QuizOptionRepository quizOptionRepository;
    @Mock private MemberDailyActivityRepository memberDailyActivityRepository;
    @Mock private MemberQuestionHistoryRepository memberQuestionHistoryRepository;
    @Mock private StockService stockService;
    @Mock private StreakPenaltyService streakPenaltyService;
    @Mock private StreakRecoveryService streakRecoveryService;
    @Mock private TransactionRetryExecutor transactionRetryExecutor;
    @Spy private Clock clock = Clock.systemUTC();

    @InjectMocks
    private DailyQuizAnswerService dailyQuizAnswerService;

    @Test
    @DisplayName("원본 답안 중복 제출 응답은 과거 스냅샷이 아닌 회원의 현재 주가를 반환한다")
    void duplicateOriginalAnswerReturnsCurrentMemberStock() {
        when(transactionRetryExecutor.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());

        Member member = Member.create(1L, "회원");
        ReflectionTestUtils.setField(member, "memberId", 1L);
        ReflectionTestUtils.setField(member, "currentStock", new BigDecimal("150.00"));

        LocalDateTime startedAt = LocalDate.of(2026, 10, 5).atTime(12, 0);
        DailyQuizSession session = DailyQuizSession.create(
                member,
                QuizCategory.MACRO_ECONOMY,
                new BigDecimal("100.00"),
                startedAt,
                false
        );
        ReflectionTestUtils.setField(session, "dailyQuizSessionId", 2L);

        QuizQuestion question = QuizQuestion.create(
                QuizCategory.MACRO_ECONOMY,
                "MULTIPLE_CHOICE",
                "문제",
                "해설",
                true
        );
        ReflectionTestUtils.setField(question, "questionId", 10L);

        QuizOption selectedOption = QuizOption.create(
                question,
                1,
                "정답",
                true
        );
        ReflectionTestUtils.setField(selectedOption, "optionId", 20L);

        DailyQuizSessionQuestion sessionQuestion =
                DailyQuizSessionQuestion.create(question, session);
        ReflectionTestUtils.setField(sessionQuestion, "sessionQuestionId", 3L);

        DailyQuizAttempt existingAttempt = DailyQuizAttempt.create(
                sessionQuestion,
                selectedOption,
                DailyQuizAttemptType.ORIGINAL,
                10,
                new BigDecimal("110.00"),
                startedAt.plusMinutes(1)
        );

        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-10-05T04:00:00Z"),
                java.time.ZoneId.of("Asia/Seoul")
        );
        ReflectionTestUtils.setField(dailyQuizAnswerService, "clock", fixedClock);

        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(dailyQuizSessionRepository.findByIdAndMemberIdForUpdate(2L, 1L))
                .thenReturn(Optional.of(session));
        when(sessionQuestionRepository
                .findBySessionQuestionIdAndDailyQuizSessionDailyQuizSessionId(3L, 2L))
                .thenReturn(Optional.of(sessionQuestion));
        when(dailyQuizAttemptRepository
                .findBySessionQuestionSessionQuestionIdAndAttemptType(
                        3L,
                        DailyQuizAttemptType.ORIGINAL
                ))
                .thenReturn(Optional.of(existingAttempt));
        when(quizOptionRepository.findByQuestionQuestionIdAndCorrectTrue(10L))
                .thenReturn(Optional.of(selectedOption));
        when(dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
                        2L,
                        DailyQuizAttemptType.ORIGINAL
                ))
                .thenReturn(2L);

        DailyQuizAnswerResponse response = (DailyQuizAnswerResponse)
                dailyQuizAnswerService.submitAnswer(
                        1L,
                        2L,
                        3L,
                        new DailyQuizAnswerRequest(
                                20L,
                                DailyQuizAttemptType.ORIGINAL
                        )
                );

        assertThat(response.currentStock())
                .isEqualByComparingTo("150.00");
        assertThat(existingAttempt.getStockAfter())
                .isEqualByComparingTo("110.00");
    }

    @ParameterizedTest
    @EnumSource(DailyQuizAttemptType.class)
    void answerUsesTimeAfterSessionLockWhenWaitingCrossesMidnight(DailyQuizAttemptType type) {
        var before = Instant.parse("2026-10-05T14:59:59Z");
        var after = Instant.parse("2026-10-05T15:00:00Z");
        var time = new AtomicReference<>(before);
        Clock movingClock = mock(Clock.class);
        when(movingClock.withZone(any())).thenReturn(movingClock);
        when(movingClock.instant()).thenAnswer(invocation -> time.get());
        when(movingClock.getZone()).thenReturn(java.time.ZoneId.of("Asia/Seoul"));
        ReflectionTestUtils.setField(dailyQuizAnswerService, "clock", movingClock);
        when(transactionRetryExecutor.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        Member member = Member.create(1L, "회원");
        if (type == DailyQuizAttemptType.ORIGINAL) {
            when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        }
        var session = DailyQuizSession.create(
                member,
                QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(),
                LocalDate.of(2026, 10, 5).atTime(12, 0),
                false);
        when(dailyQuizSessionRepository.findByIdAndMemberIdForUpdate(2L, 1L)).thenAnswer(invocation -> {
            time.set(after);
            return Optional.of(session);
        });

        assertThatThrownBy(() -> dailyQuizAnswerService.submitAnswer(
                1L, 2L, 3L, new DailyQuizAnswerRequest(4L, type)))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(DailyQuizErrorCode.SESSION_EXPIRED));

        if (type == DailyQuizAttemptType.ORIGINAL) {
            verify(streakPenaltyService)
                    .applyDuePenaltyInTransaction(1L, LocalDate.of(2026, 10, 6));
        }
        verifyNoInteractions(dailyQuizAttemptRepository, memberDailyActivityRepository, stockService);
    }
}
