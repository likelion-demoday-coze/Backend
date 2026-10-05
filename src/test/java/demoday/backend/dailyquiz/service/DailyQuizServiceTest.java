package demoday.backend.dailyquiz.service;

import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.dto.category.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.fish.service.FishService;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.MemberQuestionHistoryRepository;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.stock.service.StockService;
import demoday.backend.streak.service.StreakPenaltyService;
import demoday.backend.streak.service.StreakRecoveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.function.Supplier;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyQuizServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberPassRepository memberPassRepository;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private DailyQuizSessionRepository dailyQuizSessionRepository;

    @Mock
    private DailyQuizSessionQuestionRepository sessionQuestionRepository;

    @Mock
    private FishService fishService;

    @Mock
    private TransactionRetryExecutor transactionRetryExecutor;

    @Mock
    private DailyQuizAttemptRepository dailyQuizAttemptRepository;

    @Mock
    private QuizOptionRepository quizOptionRepository;

    @Mock
    private MemberDailyActivityRepository memberDailyActivityRepository;

    @Mock
    private MemberQuestionHistoryRepository memberQuestionHistoryRepository;

    @Mock
    private StockService stockService;

    @Mock
    private StreakPenaltyService streakPenaltyService;

    @Mock
    private StreakRecoveryService streakRecoveryService;

    @Spy
    private Clock clock = Clock.systemUTC();

    @InjectMocks
    private DailyQuizService dailyQuizService;

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(DailyQuizAttemptType.class)
    void answerUsesTimeAfterSessionLockWhenWaitingCrossesMidnight(DailyQuizAttemptType type) {
        var before = Instant.parse("2026-10-05T14:59:59Z");
        var after = Instant.parse("2026-10-05T15:00:00Z");
        var time = new AtomicReference<>(before);
        Clock movingClock = mock(Clock.class);
        when(movingClock.withZone(any())).thenReturn(movingClock);
        when(movingClock.instant()).thenAnswer(invocation -> time.get());
        when(movingClock.getZone()).thenReturn(java.time.ZoneId.of("Asia/Seoul"));
        ReflectionTestUtils.setField(dailyQuizService, "clock", movingClock);
        when(transactionRetryExecutor.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        Member member = Member.create(1L, "회원");
        if (type == DailyQuizAttemptType.ORIGINAL)
            when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        var session = DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(), LocalDate.of(2026, 10, 5).atTime(12, 0), false);
        when(dailyQuizSessionRepository.findByIdAndMemberIdForUpdate(2L, 1L)).thenAnswer(invocation -> {
            time.set(after); // 세션 잠금 대기를 마친 시점은 다음 날이다.
            return Optional.of(session);
        });
        assertThatThrownBy(() -> dailyQuizService.submitAnswer(1L, 2L, 3L, new DailyQuizAnswerRequest(4L, type)))
                .isInstanceOfSatisfying(ProjectException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(DailyQuizErrorCode.SESSION_EXPIRED));
        if (type == DailyQuizAttemptType.ORIGINAL)
            verify(streakPenaltyService).applyDuePenaltyInTransaction(1L, LocalDate.of(2026, 10, 6));
        verifyNoInteractions(dailyQuizAttemptRepository, memberDailyActivityRepository, stockService);
    }

    @Test
    @DisplayName("와이어프레임 순서대로 데일리 퀴즈 카테고리 8개를 조회한다")
    void getCategories() {
        List<DailyQuizCategoryResponse> result =
                dailyQuizService.getCategories();

        assertThat(result)
                .hasSize(8)
                .extracting(DailyQuizCategoryResponse::category)
                .containsExactly(
                        QuizCategory.MACRO_ECONOMY,
                        QuizCategory.FINANCIAL_MARKET,
                        QuizCategory.STOCK_INVESTMENT,
                        QuizCategory.INTEREST_BOND,
                        QuizCategory.EXCHANGE_GLOBAL_ECONOMY,
                        QuizCategory.REAL_ESTATE,
                        QuizCategory.CORPORATE_FINANCE,
                        QuizCategory.LIVING_ECONOMY
                );
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("일반 회원만 공통 서비스를 통해 입장료를 차감하고 패스 회원은 잔액을 유지한다")
    void delegatesFishDebitUnlessPassApplied(boolean passApplied) {
        when(transactionRetryExecutor.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        Member member = Member.create(1L, "회원");
        member.addFish(100);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(memberPassRepository.findActivePass(eq(1L), eq(PassStatus.ACTIVE), any()))
                .thenReturn(passApplied ? Optional.of(mock(MemberPass.class)) : Optional.empty());
        when(quizQuestionRepository.findAvailableQuestions(eq(1L), eq(QuizCategory.MACRO_ECONOMY), any()))
                .thenReturn(IntStream.range(0, 5)
                        .mapToObj(index -> QuizQuestion.create(QuizCategory.MACRO_ECONOMY,
                                "MULTIPLE_CHOICE", "문제 " + index, "해설", true)).toList());
        when(dailyQuizSessionRepository.save(any(DailyQuizSession.class))).thenAnswer(invocation -> {
            DailyQuizSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "dailyQuizSessionId", 23L);
            return session;
        });

        var result = dailyQuizService.createSession(1L,
                new DailyQuizSessionCreateRequest(QuizCategory.MACRO_ECONOMY));

        assertThat(result.passApplied()).isEqualTo(passApplied);
        if (passApplied) {
            verifyNoInteractions(fishService);
            assertThat(member.getFishBalance()).isEqualTo(100);
        } else {
            verify(fishService).debit(1L, 50L, FishTransactionType.DAILY_QUIZ_COST,
                    23L, "DAILY_QUIZ_SESSION:23");
        }
    }

    @Test
    @DisplayName("PREVIEW 카테고리로 데일리 퀴즈 세션을 생성할 수 없다")
    void createSessionRejectsPreviewCategory() {
        DailyQuizSessionCreateRequest request =
                new DailyQuizSessionCreateRequest(QuizCategory.PREVIEW);

        assertThatThrownBy(() ->
                dailyQuizService.createSession(1L, request)
        )
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(DailyQuizErrorCode.INVALID_CATEGORY)
                );

        verifyNoInteractions(
                memberRepository,
                memberPassRepository,
                quizQuestionRepository,
                dailyQuizSessionRepository,
                sessionQuestionRepository
        );
    }
}
