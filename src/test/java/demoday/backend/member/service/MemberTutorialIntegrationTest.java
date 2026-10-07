package demoday.backend.member.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

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
        "spring.datasource.url=jdbc:h2:mem:member-tutorial;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class MemberTutorialIntegrationTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired private MockMvc mvc;
    @Autowired private MemberService memberService;
    @Autowired private MemberRepository memberRepository;

    private Member member;

    @BeforeEach
    void setUp() {
        long sequence = SEQUENCE.incrementAndGet();
        member = memberRepository.saveAndFlush(
                Member.create(
                        980000L + sequence,
                        "t" + sequence
                )
        );
    }

    @Test
    @DisplayName("신규 회원의 내 정보에는 튜토리얼 미완료 상태가 반환된다")
    void newMemberProfileReportsTutorialIncomplete() throws Exception {
        mvc.perform(get("/api/v1/members/me")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.memberId")
                        .value(member.getMemberId()))
                .andExpect(jsonPath("$.result.tutorialCompleted")
                        .value(false));
    }

    @Test
    @DisplayName("튜토리얼 완료 API는 인증과 CSRF 토큰이 필요하다")
    void tutorialCompletionRequiresAuthenticationAndCsrf() throws Exception {
        mvc.perform(patch("/api/v1/members/me/tutorial")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/v1/members/me/tutorial")
                        .with(authentication(authToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("튜토리얼 완료 처리는 멱등하며 내 정보에도 반영된다")
    void tutorialCompletionIsIdempotent() throws Exception {
        mvc.perform(patch("/api/v1/members/me/tutorial")
                        .with(authentication(authToken()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tutorialCompleted")
                        .value(true));

        mvc.perform(patch("/api/v1/members/me/tutorial")
                        .with(authentication(authToken()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tutorialCompleted")
                        .value(true));

        mvc.perform(get("/api/v1/members/me")
                        .with(authentication(authToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tutorialCompleted")
                        .value(true));

        assertThat(memberRepository.findById(member.getMemberId())
                .orElseThrow()
                .getTutorialCompleted()).isTrue();
    }

    @Test
    @DisplayName("탈퇴 회원은 튜토리얼 완료 처리할 수 없다")
    void withdrawnMemberCannotCompleteTutorial() {
        memberService.withdraw(member.getMemberId());

        assertThatThrownBy(() ->
                memberService.completeTutorial(member.getMemberId())
        ).isInstanceOfSatisfying(
                ProjectException.class,
                exception -> assertThat(exception.getErrorCode())
                        .isEqualTo(MemberErrorCode.INACTIVE_MEMBER)
        );
    }

    private UsernamePasswordAuthenticationToken authToken() {
        return new UsernamePasswordAuthenticationToken(
                member.getMemberId(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );
    }
}
