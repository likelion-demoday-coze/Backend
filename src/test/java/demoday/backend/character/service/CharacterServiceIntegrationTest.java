package demoday.backend.character.service;

import demoday.backend.character.code.CharacterEffect;
import demoday.backend.character.code.CharacterStage;
import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import demoday.backend.dailyquiz.domain.DailyQuizSessionQuestion;
import demoday.backend.dailyquiz.dto.answer.DailyQuizAnswerRequest;
import demoday.backend.dailyquiz.repository.DailyQuizSessionRepository;
import demoday.backend.dailyquiz.repository.DailyQuizSessionQuestionRepository;
import demoday.backend.dailyquiz.service.DailyQuizService;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizOption;
import demoday.backend.quiz.domain.QuizQuestion;
import demoday.backend.quiz.repository.QuizOptionRepository;
import demoday.backend.quiz.repository.QuizQuestionRepository;
import demoday.backend.store.domain.MemberItem;
import demoday.backend.store.domain.StoreItem;
import demoday.backend.store.repository.MemberItemRepository;
import demoday.backend.store.repository.StoreItemRepository;
import demoday.backend.streak.service.StreakPenaltyService;
import demoday.backend.streak.service.StreakRecoveryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:character;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(CharacterServiceIntegrationTest.TimeConfig.class)
@Transactional
class CharacterServiceIntegrationTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    @Autowired private CharacterService characters;
    @Autowired private MemberRepository members;
    @Autowired private StreakPenaltyService penalties;
    @Autowired private StreakRecoveryService recoveries;
    @Autowired private StoreItemRepository items;
    @Autowired private MemberItemRepository inventory;
    @Autowired private TransactionRetryExecutor transactions;
    @Autowired private DailyQuizService quizzes;
    @Autowired private DailyQuizSessionRepository sessions;
    @Autowired private DailyQuizSessionQuestionRepository sessionQuestions;
    @Autowired private QuizQuestionRepository questions;
    @Autowired private QuizOptionRepository options;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;
    private Member member;
    private boolean committedFixture;
    private Long createdItemId;
    private final List<Long> createdQuestionIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        long id = SEQUENCE.incrementAndGet();
        member = members.saveAndFlush(Member.create(id, "c" + id));
    }

    @AfterEach
    void cleanCommittedFixture() {
        if (!committedFixture) return; // 일반 테스트는 Spring 테스트 트랜잭션이 롤백한다.
        transactions.execute(() -> {
            Long memberId = member.getMemberId();
            // 외래 키를 참조하는 자식 데이터부터, 이 테스트가 만든 회원 범위만 삭제한다.
            jdbc.update("delete from daily_quiz_attempt where session_question_id in (select session_question_id from daily_quiz_session_question where daily_quiz_session_id in (select daily_quiz_session_id from daily_quiz_session where member_id=?))", memberId);
            jdbc.update("delete from daily_quiz_session_question where daily_quiz_session_id in (select daily_quiz_session_id from daily_quiz_session where member_id=?)", memberId);
            jdbc.update("delete from daily_quiz_session where member_id=?", memberId);
            jdbc.update("delete from streak_recovery_event where member_id=?", memberId);
            jdbc.update("delete from stock_change where member_id=?", memberId);
            jdbc.update("delete from member_daily_activity where member_id=?", memberId);
            jdbc.update("delete from member_item where member_id=?", memberId);
            jdbc.update("delete from member_question_history where member_id=?", memberId);
            jdbc.update("delete from member where member_id=?", memberId);
            for (Long questionId : createdQuestionIds) {
                jdbc.update("delete from quiz_option where question_id=?", questionId);
                jdbc.update("delete from quiz_question where question_id=?", questionId);
                assertThat(questions.existsById(questionId)).isFalse();
            }
            // 기존 공유 상품은 유지하고, 이번 테스트에서 새로 만든 상품만 삭제한다.
            if (createdItemId != null) jdbc.update("delete from store_item where item_id=?", createdItemId);
            assertThat(members.existsById(memberId)).isFalse();
            return null;
        });
    }

    @Test
    void newMemberHasStartingStageAndBothNextGoals() {
        var response = characters.getCurrent(member.getMemberId());
        assertThat(response.stage()).isEqualTo(CharacterStage.BEGINNER);
        assertThat(response.stageName()).isEqualTo("초보냥");
        assertThat(response.nextStage()).isEqualTo(CharacterStage.BIG_HAND);
        assertThat(response.stockToNextStage()).isEqualByComparingTo("900.00");
        assertThat(response.effects()).isEmpty();
        assertThat(response.nextEffect()).isEqualTo(CharacterEffect.GLOWING_BASE);
        assertThat(response.daysToNextEffect()).isEqualTo(3);
    }

    @Test
    void nobleHasNoNextStageButProgressBetweenEffectsRemains() {
        member.changeStock(new BigDecimal("10000.00"));
        learn(member, 8, TODAY);
        var response = characters.getCurrent(member.getMemberId());
        assertThat(response.stage()).isEqualTo(CharacterStage.NOBLE);
        assertThat(response.nextStage()).isNull();
        assertThat(response.nextStageStock()).isNull();
        assertThat(response.stockToNextStage()).isNull();
        assertThat(response.effects()).containsExactly(CharacterEffect.AURA);
        assertThat(response.nextEffect()).isEqualTo(CharacterEffect.GOLDEN_HALO);
        assertThat(response.daysToNextEffect()).isEqualTo(6);
    }

    @ParameterizedTest
    @CsvSource({"0,NONE,3", "2,NONE,1", "3,GLOWING_BASE,4", "6,GLOWING_BASE,1", "7,AURA,7",
            "13,AURA,1", "14,GOLDEN_HALO,7", "20,GOLDEN_HALO,1", "21,COIN_BACKGROUND,0", "22,COIN_BACKGROUND,0"})
    void effectsChangeAtMilestonesAndHighestReplacesPrevious(int streak, String effect, int days) {
        if (streak > 0) learn(member, streak, TODAY);
        var response = characters.getCurrent(member.getMemberId());
        if (effect.equals("NONE")) assertThat(response.effects()).isEmpty();
        else assertThat(response.effects()).containsExactly(CharacterEffect.valueOf(effect));
        if (days == 0) {
            assertThat(response.nextEffect()).isNull();
            assertThat(response.daysToNextEffect()).isNull();
        } else assertThat(response.daysToNextEffect()).isEqualTo(days);
    }

    @Test
    void missedDayRemovesEffectWithoutMutatingSavedStreakOrStock() {
        learn(member, 7, TODAY.minusDays(2));
        var response = characters.getCurrent(member.getMemberId());
        assertThat(response.currentStreak()).isZero();
        assertThat(response.effects()).isEmpty();
        assertThat(member.getCurrentStreak()).isEqualTo(7);
        assertThat(member.getCurrentStock()).isEqualByComparingTo("100");
    }

    @Test
    void yesterdayMilestoneStillShowsEffectBeforeTodayLearning() {
        learn(member, 3, TODAY.minusDays(1));
        assertThat(characters.getCurrent(member.getMemberId()).effects()).containsExactly(CharacterEffect.GLOWING_BASE);
    }

    @ParameterizedTest
    @CsvSource({"1000,BIG_HAND,BEGINNER", "10000,NOBLE,BIG_HAND"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void realPenaltyAndRecoveryAutomaticallyChangeStageAndEffect(String stock, CharacterStage before, CharacterStage after) {
        committedFixture = true;
        Long memberId = member.getMemberId();
        transactions.execute(() -> {
            var saved = members.findByIdForUpdate(memberId).orElseThrow();
            saved.changeStock(new BigDecimal(stock));
            learn(saved, 3, TODAY.minusDays(2));
            return null;
        });
        assertThat(characters.getCurrent(memberId).stage()).isEqualTo(before);
        penalties.applyDuePenalty(memberId);
        assertThat(characters.getCurrent(memberId).stage()).isEqualTo(after);
        assertThat(characters.getCurrent(memberId).effects()).isEmpty();
        var item = items.findAll().stream().filter(i -> i.getItemCode().equals("STREAK_RECOVERY"))
                .findFirst().orElseGet(() -> {
                    var created = items.saveAndFlush(StoreItem.create("STREAK_RECOVERY", "복구권", 200, true, "복구"));
                    createdItemId = created.getItemId();
                    return created;
                });
        var saved = members.findById(memberId).orElseThrow();
        inventory.saveAndFlush(MemberItem.create(saved, item, 1));
        recoveries.request(memberId, recoveries.getLatest(memberId).eventId());
        var session = sessions.saveAndFlush(DailyQuizSession.create(saved, QuizCategory.MACRO_ECONOMY,
                saved.getCurrentStock(), TODAY.atTime(12, 0), false));
        for (int index = 0; index < 5; index++) {
            var question = questions.saveAndFlush(QuizQuestion.create(QuizCategory.MACRO_ECONOMY,
                    "MULTIPLE_CHOICE", "캐릭터 문제" + index, "해설", true));
            createdQuestionIds.add(question.getQuestionId());
            var wrong = options.saveAndFlush(QuizOption.create(question, 1, "오답", false));
            options.saveAndFlush(QuizOption.create(question, 2, "정답", true));
            var sq = sessionQuestions.saveAndFlush(DailyQuizSessionQuestion.create(question, session));
            quizzes.submitAnswer(memberId, session.getDailyQuizSessionId(), sq.getSessionQuestionId(),
                    new DailyQuizAnswerRequest(wrong.getOptionId(), DailyQuizAttemptType.ORIGINAL));
        }
        var response = characters.getCurrent(memberId);
        assertThat(response.stage()).isEqualTo(before);
        assertThat(response.currentStreak()).isEqualTo(5);
        assertThat(response.effects()).containsExactly(CharacterEffect.GLOWING_BASE);
    }

    @Test
    void httpUsesPrincipalAndRequiresActiveMember() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(member.getMemberId(), null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(get("/api/v1/characters/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/characters/me").with(authentication(
                new UsernamePasswordAuthenticationToken(member.getMemberId(), null, List.of(new SimpleGrantedAuthority("ROLE_GUEST"))))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/characters/me").with(authentication(auth)).param("memberId", Long.MAX_VALUE + ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.stage").value("BEGINNER"));
        var missing = new UsernamePasswordAuthenticationToken(Long.MAX_VALUE, null, List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        mvc.perform(get("/api/v1/characters/me").with(authentication(missing))).andExpect(status().isNotFound());
        jdbc.update("update member set status='WITHDRAWN' where member_id=?", member.getMemberId());
        // 기존 영속 객체 대신 새 읽기에서 탈퇴 상태를 검사한다.
        entityManager.clear();
        mvc.perform(get("/api/v1/characters/me").with(authentication(auth))).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/characters/me'].get").exists());
    }

    private static void learn(Member member, int days, LocalDate last) {
        for (int day = days - 1; day >= 0; day--) member.completeLearning(day != days - 1, last.minusDays(day));
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary Clock characterClock() { return Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneOffset.UTC); }
    }
}
