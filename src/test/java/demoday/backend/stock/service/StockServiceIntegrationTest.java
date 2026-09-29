package demoday.backend.stock.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:stock-test;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class StockServiceIntegrationTest {

    private static final AtomicLong MEMBER_SEQUENCE = new AtomicLong();

    @Autowired private StockService stockService;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MockMvc mockMvc;

    private Long memberId;

    @BeforeEach
    void createMember() {
        memberId = newMember().getMemberId();
    }

    @Test
    @DisplayName("신규 회원은 초기 주가 100.00을 공통 응답 형식으로 조회한다")
    void initialStock() throws Exception {
        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("100.00");
        mockMvc.perform(get("/api/v1/stocks/me").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.result.currentStock").value(100.00));
    }

    @Test
    @DisplayName("주가가 변경되면 저장된 소수점 값을 그대로 조회한다")
    void changedStock() throws Exception {
        Member member = memberRepository.findById(memberId).orElseThrow();
        member.increaseStock(3);
        member.increaseStock(7);
        memberRepository.saveAndFlush(member);

        assertThat(stockService.getCurrentStock(memberId).currentStock()).isEqualByComparingTo("110.21");
        mockMvc.perform(get("/api/v1/stocks/me").with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentStock").value(110.21));
    }

    @Test
    @DisplayName("다른 회원 ID를 요청에 넣어도 인증된 본인의 주가만 조회한다")
    void usesAuthenticatedMember() throws Exception {
        Member other = newMember();
        other.increaseStock(10);
        memberRepository.saveAndFlush(other);

        mockMvc.perform(get("/api/v1/stocks/me")
                        .param("memberId", other.getMemberId().toString())
                        .with(memberAuthentication(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.currentStock").value(100.00));
    }

    @Test
    @DisplayName("탈퇴 회원의 조회는 거절한다")
    void withdrawnMember() throws Exception {
        jdbcTemplate.update("update member set status = 'WITHDRAWN' where member_id = ?", memberId);

        mockMvc.perform(get("/api/v1/stocks/me").with(memberAuthentication(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("STOCK_403_1"));
    }

    @Test
    @DisplayName("인증 정보의 회원이 DB에 없으면 404를 반환한다")
    void missingMember() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me").with(memberAuthentication(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_404"));
    }

    @Test
    @DisplayName("비로그인 요청은 기존 카카오 로그인 경로로 이동한다")
    void anonymousRequest() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/api/v1/auth/oauth2/authorization/kakao"));
    }

    @Test
    @DisplayName("MEMBER 권한이 없는 인증 사용자는 조회할 수 없다")
    void requiresMemberRole() throws Exception {
        mockMvc.perform(get("/api/v1/stocks/me").with(authentication(
                        new UsernamePasswordAuthenticationToken(memberId, null,
                                List.of(new SimpleGrantedAuthority("ROLE_GUEST"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("서비스에 회원 ID가 없으면 인증 오류로 처리한다")
    void missingPrincipal() {
        assertThatThrownBy(() -> stockService.getCurrentStock(null))
                .isInstanceOfSatisfying(ProjectException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(GeneralErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("Swagger에 주가 조회 API와 응답 모델이 등록된다")
    void apiDocumentation() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/stocks/me'].get").exists())
                .andExpect(jsonPath("$.components.schemas.StockResponse.properties.currentStock").exists());
    }

    private Member newMember() {
        long sequence = MEMBER_SEQUENCE.incrementAndGet();
        return memberRepository.saveAndFlush(Member.create(sequence, "stock" + sequence));
    }

    private RequestPostProcessor memberAuthentication(Long id) {
        return authentication(new UsernamePasswordAuthenticationToken(id, null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))));
    }
}
