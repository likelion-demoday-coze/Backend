package demoday.backend.character.service;

import demoday.backend.character.code.CharacterEffect;
import demoday.backend.character.code.CharacterErrorCode;
import demoday.backend.character.code.CharacterStage;
import demoday.backend.character.dto.CharacterEvolutionConfirmRequest;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockChangeType;
import demoday.backend.stock.service.StockService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:character;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
@Import(CharacterServiceIntegrationTest.TimeConfig.class)
@Transactional
class CharacterServiceIntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 5);
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired private CharacterService characters;
    @Autowired private StockService stocks;
    @Autowired private MemberRepository members;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;
    @Autowired private EntityManager entityManager;

    private Member member;

    @BeforeEach
    void setUp() {
        long id = SEQUENCE.incrementAndGet();
        member = members.saveAndFlush(Member.create(id, "c" + id));
    }

    @Test
    void newMemberStartsAsBasicAndGuidesNextStockStage() {
        var response = characters.getCurrent(member.getMemberId());

        assertThat(response.stage()).isEqualTo(CharacterStage.BASIC);
        assertThat(response.stageName()).isEqualTo("기본냥");
        assertThat(response.pendingEvolutionStage()).isNull();
        assertThat(response.nextStage()).isEqualTo(CharacterStage.BIG_HAND);
        assertThat(response.nextStageStock()).isEqualByComparingTo("250.00");
        assertThat(response.stockToNextStage()).isEqualByComparingTo("150.00");
        assertThat(response.correctAnswersToNextStage()).isNull();
        assertThat(response.effects()).isEmpty();
        assertThat(response.nextEffect()).isEqualTo(CharacterEffect.PAW_PRINT);
        assertThat(response.daysToNextEffect()).isEqualTo(2);
    }

    @Test
    void babyGuidesOneCorrectAnswerInsteadOfStockThreshold() {
        change("100.00", "80.00", StockChangeType.ADMIN_ADJUSTMENT, "baby");

        var response = characters.getCurrent(member.getMemberId());

        assertThat(response.stage()).isEqualTo(CharacterStage.BABY);
        assertThat(response.nextStage()).isEqualTo(CharacterStage.BASIC);
        assertThat(response.nextStageStock()).isNull();
        assertThat(response.stockToNextStage()).isNull();
        assertThat(response.correctAnswersToNextStage()).isEqualTo(1);
    }

    @Test
    void royalHasNoNextStageButKeepsStreakProgress() {
        change("100.00", "1500.00", StockChangeType.ADMIN_ADJUSTMENT, "royal");
        learn(member, 8, TODAY);

        var response = characters.getCurrent(member.getMemberId());

        assertThat(response.stage()).isEqualTo(CharacterStage.ROYAL);
        assertThat(response.nextStage()).isNull();
        assertThat(response.nextStageStock()).isNull();
        assertThat(response.stockToNextStage()).isNull();
        assertThat(response.correctAnswersToNextStage()).isNull();
        assertThat(response.effects()).containsExactly(CharacterEffect.AURA);
        assertThat(response.nextEffect()).isEqualTo(CharacterEffect.GINKGO_LEAF);
        assertThat(response.daysToNextEffect()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            "0,NONE,2", "1,NONE,1", "2,PAW_PRINT,1",
            "3,GLOWING_BASE,2", "4,GLOWING_BASE,1", "5,FEATHER,2", "6,FEATHER,1",
            "7,AURA,3", "9,AURA,1", "10,GINKGO_LEAF,4", "13,GINKGO_LEAF,1",
            "14,GOLDEN_HALO,3", "16,GOLDEN_HALO,1", "17,GOLDEN_FLOWER,4", "20,GOLDEN_FLOWER,1",
            "21,COIN_BACKGROUND,0", "22,COIN_BACKGROUND,0"
    })
    void effectsChangeAtStreakMilestones(int streak, String effect, int days) {
        if (streak > 0) learn(member, streak, TODAY);

        var response = characters.getCurrent(member.getMemberId());

        if (effect.equals("NONE")) {
            assertThat(response.effects()).isEmpty();
        } else {
            assertThat(response.effects()).containsExactly(CharacterEffect.valueOf(effect));
        }
        if (days == 0) {
            assertThat(response.nextEffect()).isNull();
            assertThat(response.daysToNextEffect()).isNull();
        } else {
            assertThat(response.daysToNextEffect()).isEqualTo(days);
            assertThat(response.nextEffect().getMinimumStreak()).isEqualTo(streak + days);
        }
    }

    @Test
    void expiredStreakDoesNotExposeSavedEffect() {
        learn(member, 7, TODAY.minusDays(2));

        var response = characters.getCurrent(member.getMemberId());

        assertThat(response.currentStreak()).isZero();
        assertThat(response.effects()).isEmpty();
        assertThat(member.getCurrentStreak()).isEqualTo(7);
    }

    @Test
    void httpReturnsNewEffectAndNextMilestoneForFiveDayStreak() throws Exception {
        learn(member, 5, TODAY);

        mvc.perform(get("/api/v1/characters/me").with(authentication(memberAuthentication(member.getMemberId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.effects[0]").value("FEATHER"))
                .andExpect(jsonPath("$.result.nextEffect").value("AURA"))
                .andExpect(jsonPath("$.result.daysToNextEffect").value(2));
    }

    @Test
    void quizCorrectCrossingThresholdCreatesPendingEvolutionAndConfirmationClearsIt() {
        change("100.00", "249.00", StockChangeType.ADMIN_ADJUSTMENT, "setup-big-hand");
        change("249.00", "251.49", StockChangeType.QUIZ_CORRECT, "evolve-big-hand");

        assertThat(characters.getCurrent(member.getMemberId()).pendingEvolutionStage())
                .isEqualTo(CharacterStage.BIG_HAND);

        characters.confirmEvolution(member.getMemberId(), new CharacterEvolutionConfirmRequest(CharacterStage.BIG_HAND));

        assertThat(characters.getCurrent(member.getMemberId()).pendingEvolutionStage()).isNull();
        characters.confirmEvolution(member.getMemberId(), new CharacterEvolutionConfirmRequest(CharacterStage.BIG_HAND));
        assertThat(characters.getCurrent(member.getMemberId()).pendingEvolutionStage()).isNull();
    }

    @Test
    void babyEvolvesAfterOneCorrectAnswerEvenWhenStockRemainsBelowOneHundred() {
        change("100.00", "80.00", StockChangeType.ADMIN_ADJUSTMENT, "setup-baby");
        change("80.00", "80.80", StockChangeType.QUIZ_CORRECT, "evolve-basic");

        var response = characters.getCurrent(member.getMemberId());

        assertThat(response.currentStock()).isEqualByComparingTo("80.80");
        assertThat(response.stage()).isEqualTo(CharacterStage.BASIC);
        assertThat(response.pendingEvolutionStage()).isEqualTo(CharacterStage.BASIC);
    }

    @Test
    void staleEvolutionConfirmationCannotClearNewerPendingStage() {
        change("100.00", "250.00", StockChangeType.ADMIN_ADJUSTMENT, "pending-big-hand");

        assertThatThrownBy(() -> characters.confirmEvolution(
                member.getMemberId(),
                new CharacterEvolutionConfirmRequest(CharacterStage.BASIC)
        )).isInstanceOfSatisfying(ProjectException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(CharacterErrorCode.EVOLUTION_STAGE_MISMATCH));

        assertThat(characters.getCurrent(member.getMemberId()).pendingEvolutionStage())
                .isEqualTo(CharacterStage.BIG_HAND);
    }

    @Test
    void httpUsesPrincipalAndExposesEvolutionPatch() throws Exception {
        var auth = memberAuthentication(member.getMemberId());

        mvc.perform(get("/api/v1/characters/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/characters/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.stage").value("BASIC"));

        change("100.00", "250.00", StockChangeType.ADMIN_ADJUSTMENT, "http-evolution");
        mvc.perform(patch("/api/v1/characters/me/evolution")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"BIG_HAND\"}"))
                .andExpect(status().isOk());
        assertThat(characters.getCurrent(member.getMemberId()).pendingEvolutionStage()).isNull();

        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/characters/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/characters/me/evolution'].patch").exists());

        jdbc.update("update member set status='WITHDRAWN' where member_id=?", member.getMemberId());
        entityManager.clear();
        mvc.perform(get("/api/v1/characters/me").with(authentication(auth)))
                .andExpect(status().isUnauthorized());
    }

    private void change(String before, String after, StockChangeType type, String suffix) {
        stocks.changeStock(
                member.getMemberId(),
                new BigDecimal(before),
                new BigDecimal(after),
                type,
                null,
                "character:" + member.getMemberId() + ":" + suffix
        );
    }

    private static UsernamePasswordAuthenticationToken memberAuthentication(Long memberId) {
        return new UsernamePasswordAuthenticationToken(
                memberId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
    }

    private static void learn(Member member, int days, LocalDate last) {
        for (int day = days - 1; day >= 0; day--) {
            member.completeLearning(day != days - 1, last.minusDays(day));
        }
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean
        @Primary
        Clock characterClock() {
            return Clock.fixed(Instant.parse("2026-10-05T03:00:00Z"), ZoneOffset.UTC);
        }
    }
}
