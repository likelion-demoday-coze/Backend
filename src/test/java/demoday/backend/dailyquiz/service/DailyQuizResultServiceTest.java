package demoday.backend.dailyquiz.service;

import demoday.backend.dailyquiz.code.DailyQuizErrorCode;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.repository.DailyQuizAttemptRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.repository.QuizOptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyQuizResultServiceTest {

    @Mock private DailyQuizSessionRepository dailyQuizSessionRepository;
    @Mock private DailyQuizAttemptRepository dailyQuizAttemptRepository;
    @Mock private QuizOptionRepository quizOptionRepository;
    @Spy private Clock clock = Clock.systemUTC();

    @InjectMocks
    private DailyQuizResultService dailyQuizResultService;

    @Test
    @DisplayName("진행 중인 세션은 결과를 조회할 수 없다")
    void getResultRejectsInProgressSession() {
        Member member = Member.create(1L, "회원");
        DailyQuizSession session = DailyQuizSession.create(
                member,
                QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(),
                LocalDateTime.now().minusMinutes(1),
                false);
        when(dailyQuizSessionRepository.findByDailyQuizSessionIdAndMemberMemberId(2L, 1L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> dailyQuizResultService.getResult(1L, 2L))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(DailyQuizErrorCode.INVALID_SESSION_STATE));
    }

    @Test
    @DisplayName("원본 풀이가 끝나지 않은 세션은 완료할 수 없다")
    void completeSessionRejectsInProgressSession() {
        Member member = Member.create(1L, "회원");
        DailyQuizSession session = DailyQuizSession.create(
                member,
                QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(),
                LocalDateTime.now().minusMinutes(1),
                false);
        when(dailyQuizSessionRepository.findByIdAndMemberIdForUpdate(2L, 1L))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(() -> dailyQuizResultService.completeSession(1L, 2L))
                .isInstanceOfSatisfying(
                        ProjectException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(DailyQuizErrorCode.INVALID_SESSION_STATE));
    }
}
