package demoday.backend.streak.service;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:streak;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(StreakServiceIntegrationTest.TimeConfig.class)
@Transactional
class StreakServiceIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private StreakService streaks;
    @Autowired private MemberRepository members;
    @Autowired private DailyQuizSessionRepository sessions;
    @Autowired private DailyQuizSessionQuestionRepository sessionQuestions;
    @Autowired private QuizQuestionRepository questions;
    @Autowired private QuizOptionRepository options;
    @Autowired private DailyQuizService quizzes;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc mvc;
    private Member member;

    @BeforeEach
    void setUp() {
        long number = SEQUENCE.incrementAndGet();
        member = members.saveAndFlush(Member.create(number, "s" + number));
    }

    @Test
    void newMemberHasNoCompletedLearningAndUsesKstDate() {
        var result = streaks.getCurrent(member.getMemberId());
        assertThat(result.date()).isEqualTo(TODAY);
        assertThat(result.currentStreak()).isZero();
        assertThat(result.learnedToday()).isFalse();
        assertThat(result.lastLearningDate()).isNull();
    }

    @Test
    void yesterdayRemainsContinuousAndTodayIsCountedOnce() {
        member.completeLearning(false, TODAY.minusDays(3));
        member.completeLearning(true, TODAY.minusDays(2));
        member.completeLearning(true, TODAY.minusDays(1));
        assertThat(streaks.getCurrent(member.getMemberId()).currentStreak()).isEqualTo(3);
        member.completeLearning(true, TODAY);
        member.completeLearning(true, TODAY);
        var result = streaks.getCurrent(member.getMemberId());
        assertThat(result.currentStreak()).isEqualTo(4);
        assertThat(result.learnedToday()).isTrue();
    }

    @Test
    void missedDayDisplaysZeroWithoutDestroyingRecoveryDataOrChangingStock() {
        member.completeLearning(false, TODAY.minusDays(4));
        member.completeLearning(true, TODAY.minusDays(3));
        member.completeLearning(true, TODAY.minusDays(2));
        var result = streaks.getCurrent(member.getMemberId());
        assertThat(result.currentStreak()).isZero();
        assertThat(result.lastLearningDate()).isEqualTo(TODAY.minusDays(2));
        assertThat(member.getCurrentStreak()).isEqualTo(3);
        assertThat(member.getCurrentStock()).isEqualByComparingTo("100");
        assertThat(jdbc.queryForObject("select count(*) from stock_change where member_id=?",
                Long.class, member.getMemberId())).isZero();
        member.completeLearning(false, TODAY);
        assertThat(streaks.getCurrent(member.getMemberId()).currentStreak()).isEqualTo(1);
    }

    @Test
    void legacyMemberUsesOriginalCompletionEvenAfterRetrySessionExpired() {
        jdbc.update("update member set current_streak=3 where member_id=?", member.getMemberId());
        var session = DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(), TODAY.minusDays(1).atTime(12, 0), false);
        session.completeOriginal(member.getCurrentStock());
        session.expire(TODAY.atStartOfDay());
        sessions.saveAndFlush(session);
        entityManager.clear();
        var result = streaks.getCurrent(member.getMemberId());
        assertThat(result.currentStreak()).isEqualTo(3);
        assertThat(result.lastLearningDate()).isEqualTo(TODAY.minusDays(1));
        assertThat(result.learnedToday()).isFalse();
    }

    @Test
    void partialQuizDoesNotCountAsLearning() {
        sessions.saveAndFlush(DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(), TODAY.atTime(12, 0), false));
        jdbc.update("update member set current_streak=7 where member_id=?", member.getMemberId());
        entityManager.clear();
        var result = streaks.getCurrent(member.getMemberId());
        assertThat(result.currentStreak()).isZero();
        assertThat(result.lastLearningDate()).isNull();
    }

    @Test
    void httpUsesPrincipalAndRejectsUnauthorizedAndWithdrawnMembers() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(member.getMemberId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(get("/api/v1/streaks/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/streaks/me").with(authentication(
                new UsernamePasswordAuthenticationToken(member.getMemberId(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_GUEST")))))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/streaks/me").param("memberId", "999999").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.currentStreak").value(0))
                .andExpect(jsonPath("$.result.date").value("2026-10-05"));
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", member.getMemberId());
        entityManager.clear();
        mvc.perform(get("/api/v1/streaks/me").with(authentication(auth)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("STREAK_403_1"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void onlyFifthOriginalAnswerRecordsLearningAndReplayDoesNotIncrement() {
        // 답안 처리의 가장 바깥 재시도 트랜잭션을 실제로 실행한다.
        LocalDateTime now = TODAY.atStartOfDay();
        var session = sessions.saveAndFlush(DailyQuizSession.create(member, QuizCategory.MACRO_ECONOMY,
                member.getCurrentStock(), now, false));
        Long lastQuestionId = null;
        Long lastOptionId = null;
        for (int index = 0; index < 5; index++) {
            var question = questions.saveAndFlush(QuizQuestion.create(
                    QuizCategory.MACRO_ECONOMY, "MULTIPLE_CHOICE", "문제" + index, "해설", true));
            var wrongOption = options.saveAndFlush(QuizOption.create(question, 1, "오답", false));
            options.saveAndFlush(QuizOption.create(question, 2, "정답", true));
            var sessionQuestion = sessionQuestions.saveAndFlush(DailyQuizSessionQuestion.create(question, session));
            lastQuestionId = sessionQuestion.getSessionQuestionId();
            lastOptionId = wrongOption.getOptionId();
            quizzes.submitAnswer(member.getMemberId(), session.getDailyQuizSessionId(), lastQuestionId,
                    new DailyQuizAnswerRequest(lastOptionId, DailyQuizAttemptType.ORIGINAL));
            var saved = members.findById(member.getMemberId()).orElseThrow();
            assertThat(saved.getCurrentStreak()).isEqualTo(index == 4 ? 1 : 0);
            assertThat(saved.getLastLearningDate()).isEqualTo(index == 4 ? now.toLocalDate() : null);
        }
        quizzes.submitAnswer(member.getMemberId(), session.getDailyQuizSessionId(), lastQuestionId,
                new DailyQuizAnswerRequest(lastOptionId, DailyQuizAttemptType.ORIGINAL));
        assertThat(members.findById(member.getMemberId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean
        @Primary
        Clock streakClock() {
            return Clock.fixed(Instant.parse("2026-10-04T15:00:00Z"), ZoneOffset.UTC);
        }
    }
}
