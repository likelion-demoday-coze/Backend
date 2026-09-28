package demoday.backend.dailyquiz.service;

import demoday.backend.activity.repository.MemberDailyActivityRepository;
import demoday.backend.dailyquiz.dto.category.DailyQuizCategoryResponse;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.dto.session.DailyQuizSessionCreateRequest;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.fish.service.FishService;
import demoday.backend.fish.code.FishTransactionType;
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
import demoday.backend.stock.repository.StockChangeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
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
    private DailyQuizAttemptRepository dailyQuizAttemptRepository;

    @Mock
    private QuizOptionRepository quizOptionRepository;

    @Mock
    private MemberDailyActivityRepository memberDailyActivityRepository;

    @Mock
    private MemberQuestionHistoryRepository memberQuestionHistoryRepository;

    @Mock
    private StockChangeRepository stockChangeRepository;

    @InjectMocks
    private DailyQuizService dailyQuizService;

    @Test
    @DisplayName("와이어프레임 순서대로 데일리 퀴즈 카테고리 8개를 조회한다")
    void getCategories() {
        List<DailyQuizCategoryResponse> result =
                dailyQuizService.getCategories();

        assertThat(result)
                .hasSize(8)
                .extracting(DailyQuizCategoryResponse::category)
                .containsExactly(QuizCategory.values());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("일반 회원만 공통 서비스를 통해 입장료를 차감하고 패스 회원은 잔액을 유지한다")
    void delegatesFishDebitUnlessPassApplied(boolean passApplied) {
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
}
