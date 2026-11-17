package demoday.backend.dailyquiz.service;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.dto.category.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.dto.session.DailyQuizTodayResponse;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.streak.service.StreakPenaltyService;
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

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DailyQuizSessionServiceTest {

    @Mock private MemberRepository memberRepository;
    @Mock private MemberPassRepository memberPassRepository;
    @Mock private QuizQuestionRepository quizQuestionRepository;
    @Mock private DailyQuizSessionRepository dailyQuizSessionRepository;
    @Mock private DailyQuizSessionQuestionRepository sessionQuestionRepository;
    @Mock private FishService fishService;
    @Mock private TransactionRetryExecutor transactionRetryExecutor;
    @Mock private DailyQuizAttemptRepository dailyQuizAttemptRepository;
    @Mock private QuizOptionRepository quizOptionRepository;
    @Mock private StreakPenaltyService streakPenaltyService;
    @Spy private Clock clock = Clock.systemUTC();

    @InjectMocks
    private DailyQuizSessionService dailyQuizSessionService;

    @Test
    @DisplayName("일반 회원의 오늘 정규장 주가 반영 가능 횟수를 조회한다")
    void getTodayAvailabilityWithoutPass() {
        when(memberPassRepository.findActivePass(eq(1L), eq(PassStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionMemberMemberIdAndAttemptTypeAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                        eq(1L), eq(DailyQuizAttemptType.ORIGINAL), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(4L);

        DailyQuizTodayResponse result = dailyQuizSessionService.getTodayAvailability(1L);

        assertThat(result.passApplied()).isFalse();
        assertThat(result.stockReflectionLimit()).isEqualTo(2);
        assertThat(result.todayOriginalAnswerCount()).isEqualTo(4);
        assertThat(result.usedStockReflectionCount()).isEqualTo(1);
        assertThat(result.remainingStockReflectionCount()).isEqualTo(1);
        assertThat(result.fishCost()).isEqualTo(50L);
    }

    @Test
    @DisplayName("오늘 제출한 원본 답안이 없으면 주가 반영 사용 횟수는 0회다")
    void getTodayAvailabilityWithoutOriginalAnswer() {
        when(memberPassRepository.findActivePass(eq(1L), eq(PassStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(Optional.empty());
        when(dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionMemberMemberIdAndAttemptTypeAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                        eq(1L), eq(DailyQuizAttemptType.ORIGINAL), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(0L);

        DailyQuizTodayResponse result = dailyQuizSessionService.getTodayAvailability(1L);

        assertThat(result.todayOriginalAnswerCount()).isZero();
        assertThat(result.usedStockReflectionCount()).isZero();
        assertThat(result.remainingStockReflectionCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("패스 회원의 사용 횟수는 주가 반영 한도를 초과하지 않는다")
    void getTodayAvailabilityWithPass() {
        when(memberPassRepository.findActivePass(eq(1L), eq(PassStatus.ACTIVE), any(LocalDateTime.class)))
                .thenReturn(Optional.of(mock(MemberPass.class)));
        when(dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionMemberMemberIdAndAttemptTypeAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                        eq(1L), eq(DailyQuizAttemptType.ORIGINAL), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(16L);

        DailyQuizTodayResponse result = dailyQuizSessionService.getTodayAvailability(1L);

        assertThat(result.passApplied()).isTrue();
        assertThat(result.stockReflectionLimit()).isEqualTo(3);
        assertThat(result.todayOriginalAnswerCount()).isEqualTo(16);
        assertThat(result.usedStockReflectionCount()).isEqualTo(3);
        assertThat(result.remainingStockReflectionCount()).isZero();
        assertThat(result.fishCost()).isZero();
    }

    @Test
    @DisplayName("와이어프레임 순서대로 데일리 퀴즈 카테고리 8개를 조회한다")
    void getCategories() {
        List<DailyQuizCategoryResponse> result = dailyQuizSessionService.getCategories();

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
                        QuizCategory.LIVING_ECONOMY);
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
                        .mapToObj(index -> QuizQuestion.create(
                                QuizCategory.MACRO_ECONOMY, "MULTIPLE_CHOICE", "문제 " + index, "해설", true))
                        .toList());
        when(dailyQuizSessionRepository.save(any(DailyQuizSession.class))).thenAnswer(invocation -> {
            DailyQuizSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "dailyQuizSessionId", 23L);
            return session;
        });

        var result = dailyQuizSessionService.createSession(
                1L, new DailyQuizSessionCreateRequest(QuizCategory.MACRO_ECONOMY));

        assertThat(result.passApplied()).isEqualTo(passApplied);
        assertThat(result.stockReflectionApplied()).isTrue();
        if (passApplied) {
            verifyNoInteractions(fishService);
            assertThat(member.getFishBalance()).isEqualTo(100);
        } else {
            verify(fishService).debit(
                    1L, 50L, FishTransactionType.DAILY_QUIZ_COST, 23L, "DAILY_QUIZ_SESSION:23");
        }
    }

    @Test
    @DisplayName("일반 회원이 오늘 주가 반영 세션을 모두 사용하면 새 세션은 미반영으로 생성한다")
    void createsSessionWithoutStockReflectionWhenDailyLimitIsExhausted() {
        when(transactionRetryExecutor.execute(any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(0)).get());
        Member member = Member.create(1L, "회원");
        member.addFish(100);
        when(memberRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(member));
        when(memberPassRepository.findActivePass(eq(1L), eq(PassStatus.ACTIVE), any()))
                .thenReturn(Optional.empty());
        when(dailyQuizAttemptRepository
                .countBySessionQuestionDailyQuizSessionMemberMemberIdAndAttemptTypeAndAnsweredAtGreaterThanEqualAndAnsweredAtLessThan(
                        eq(1L), eq(DailyQuizAttemptType.ORIGINAL), any(), any()))
                .thenReturn(10L);
        when(quizQuestionRepository.findAvailableQuestions(eq(1L), eq(QuizCategory.MACRO_ECONOMY), any()))
                .thenReturn(IntStream.range(0, 5)
                        .mapToObj(index -> QuizQuestion.create(
                                QuizCategory.MACRO_ECONOMY, "MULTIPLE_CHOICE", "문제 " + index, "해설", true))
                        .toList());
        when(dailyQuizSessionRepository.save(any(DailyQuizSession.class))).thenAnswer(invocation -> {
            DailyQuizSession session = invocation.getArgument(0);
            ReflectionTestUtils.setField(session, "dailyQuizSessionId", 24L);
            return session;
        });

        var result = dailyQuizSessionService.createSession(
                1L, new DailyQuizSessionCreateRequest(QuizCategory.MACRO_ECONOMY));

        assertThat(result.stockReflectionApplied()).isFalse();
    }

    @Test
    @DisplayName("PREVIEW 카테고리로 데일리 퀴즈 세션을 생성할 수 없다")
    void createSessionRejectsPreviewCategory() {
        DailyQuizSessionCreateRequest request = new DailyQuizSessionCreateRequest(QuizCategory.PREVIEW);

        assertThatThrownBy(() -> dailyQuizSessionService.createSession(1L, request))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(DailyQuizErrorCode.INVALID_CATEGORY));

        verifyNoInteractions(
                memberRepository,
                memberPassRepository,
                quizQuestionRepository,
                dailyQuizSessionRepository,
                sessionQuestionRepository);
    }
}
