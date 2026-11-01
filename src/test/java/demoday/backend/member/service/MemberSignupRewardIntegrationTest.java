package demoday.backend.member.service;

import demoday.backend.auth.service.KakaoAuthService;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.servlet.http.Cookie;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:signup-reward;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
@AutoConfigureMockMvc
class MemberSignupRewardIntegrationTest {
    private static final AtomicLong SEQUENCE = new AtomicLong(1200000);
    private static final String POPUP_URL = "/api/v1/members/me/signup-reward/popup";

    @Autowired MemberService members;
    @Autowired MemberRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SessionRepository<?> sessions;
    @MockitoSpyBean FishService fish;

    private Member signup() {
        long id = SEQUENCE.incrementAndGet();
        return members.signup(id, "s" + (id - 1200000));
    }

    private UsernamePasswordAuthenticationToken auth(Member member) {
        return new UsernamePasswordAuthenticationToken(member.getMemberId(), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
    }

    private long rewardCount(Long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM fish_transaction "
                + "WHERE member_id = ? AND transaction_type = 'SIGNUP_REWARD'", Long.class, memberId);
    }

    private <S extends Session> Cookie pendingSignupCookie(SessionRepository<S> store, long kakaoId) {
        S session = store.createSession();
        session.setAttribute(KakaoAuthService.PENDING_KAKAO_USER_ID, kakaoId);
        store.save(session);
        return new Cookie("SESSION", Base64.getEncoder().encodeToString(
                session.getId().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void signupCredits200AndRecordsOneTransaction() {
        Member member = signup();
        assertThat(repository.findById(member.getMemberId()).orElseThrow().getFishBalance()).isEqualTo(200L);
        assertThat(members.getMyProfile(member.getMemberId()).signupRewardPopupPending()).isTrue();
        assertThat(rewardCount(member.getMemberId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount FROM fish_transaction WHERE idempotency_key = ?",
                Long.class, "SIGNUP_REWARD:" + member.getMemberId())).isEqualTo(200L);
        assertThat(jdbc.queryForObject("SELECT balance_after FROM fish_transaction WHERE idempotency_key = ?",
                Long.class, "SIGNUP_REWARD:" + member.getMemberId())).isEqualTo(200L);
    }

    @Test
    void duplicateSignupDoesNotCreditAgain() {
        Member member = signup();
        assertThatThrownBy(() -> members.signup(member.getKakaoUserId(), member.getNickname()))
                .isInstanceOf(ProjectException.class);
        assertThat(repository.findById(member.getMemberId()).orElseThrow().getFishBalance()).isEqualTo(200L);
        assertThat(rewardCount(member.getMemberId())).isEqualTo(1);
    }

    @Test
    void creditFailureRollsBackMemberCreation() {
        long id = SEQUENCE.incrementAndGet();
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM fish_transaction", Long.class);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("failure after credit");
        })
                .when(fish).credit(anyLong(), eq(200L), eq(FishTransactionType.SIGNUP_REWARD), anyLong(), anyString());
        assertThatThrownBy(() -> members.signup(id, "r" + (id - 1200000)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(repository.findByKakaoUserId(id)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM fish_transaction", Long.class)).isEqualTo(before);
    }

    @Test
    void signupEndpointReturnsPendingFlagAndCreditedBalance() throws Exception {
        long id = SEQUENCE.incrementAndGet();
        mvc.perform(post("/api/v1/auth/signup").cookie(pendingSignupCookie(sessions, id)).with(csrf())
                        .contentType("application/json")
                        .content("{\"nickname\":\"a" + (id - 1200000) + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.signupRewardPopupPending").value(true));
        assertThat(repository.findByKakaoUserId(id).orElseThrow().getFishBalance()).isEqualTo(200L);
    }

    @Test
    void completingPopupIsIdempotentAndDoesNotChangeAnyBalanceOrOtherMember() throws Exception {
        Member member = signup();
        Member other = signup();
        fish.debit(member.getMemberId(), 50L, FishTransactionType.DAILY_QUIZ_COST,
                null, "SIGNUP_TEST_COST:" + member.getMemberId());
        for (int i = 0; i < 2; i++) {
            mvc.perform(patch(POPUP_URL).with(authentication(auth(member))).with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result.signupRewardPopupPending").value(false));
        }
        mvc.perform(get("/api/v1/members/me").with(authentication(auth(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.signupRewardPopupPending").value(false));
        assertThat(repository.findById(member.getMemberId()).orElseThrow().getFishBalance()).isEqualTo(150L);
        assertThat(members.getMyProfile(other.getMemberId()).signupRewardPopupPending()).isTrue();
        assertThat(rewardCount(member.getMemberId())).isEqualTo(1);
    }

    @Test
    void existingMemberHasNoPopupAndCompletionDoesNotGrantReward() throws Exception {
        long id = SEQUENCE.incrementAndGet();
        Member legacy = repository.saveAndFlush(Member.create(id, "l" + (id - 1200000)));
        assertThat(members.getMyProfile(legacy.getMemberId()).signupRewardPopupPending()).isFalse();
        mvc.perform(patch(POPUP_URL).with(authentication(auth(legacy))).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.signupRewardPopupPending").value(false));
        assertThat(repository.findById(legacy.getMemberId()).orElseThrow().getFishBalance()).isZero();
        assertThat(rewardCount(legacy.getMemberId())).isZero();
    }

    @Test
    void popupRequiresAuthenticationAndCsrf() throws Exception {
        Member member = signup();
        mvc.perform(patch(POPUP_URL).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(patch(POPUP_URL).with(authentication(auth(member)))).andExpect(status().isForbidden());
        assertThat(members.getMyProfile(member.getMemberId()).signupRewardPopupPending()).isTrue();
    }

    @Test
    void withdrawnMemberCannotCompletePopup() throws Exception {
        Member member = signup();
        members.withdraw(member.getMemberId());
        mvc.perform(patch(POPUP_URL).with(authentication(auth(member))).with(csrf()))
                .andExpect(status().isUnauthorized());
        assertThatThrownBy(() -> members.completeSignupRewardPopup(member.getMemberId()))
                .isInstanceOf(ProjectException.class);
    }

    @Test
    void signupRewardIsCreditOnly() {
        assertThat(FishTransactionType.SIGNUP_REWARD.allowsCredit()).isTrue();
        assertThat(FishTransactionType.SIGNUP_REWARD.allowsDebit()).isFalse();
    }
}
