package demoday.backend.dailyquiz.service;

import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.repository.MemberQuestionHistoryRepository;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.stock.service.StockService;
import demoday.backend.streak.service.StreakPenaltyService;
import demoday.backend.streak.service.StreakRecoveryService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
