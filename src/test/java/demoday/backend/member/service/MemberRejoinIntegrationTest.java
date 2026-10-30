package demoday.backend.member.service;

import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:member-rejoin;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.open-in-view=false"
})
@Import(MemberRejoinIntegrationTest.FixedClockConfig.class)
class MemberRejoinIntegrationTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Instant NOW =
            Instant.parse("2026-10-30T06:00:00Z");
    private static final LocalDateTime NOW_LOCAL =
            LocalDateTime.ofInstant(NOW, KST);

    private static final Long KAKAO_USER_ID = 990001L;
    private static final String NICKNAME = "재가입회원";

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    @DisplayName("탈퇴 후 동일한 카카오 계정으로 신규 회원처럼 재가입한다")
    void rejoinWithSameKakaoAccountAsNewMember() {
        // given: 기존 회원이 자산과 학습 기록을 보유하고 있다.
        Member previousMember = memberRepository.saveAndFlush(
                Member.create(KAKAO_USER_ID, NICKNAME)
        );

        Long previousMemberId = previousMember.getMemberId();

        previousMember.addFish(500L);
        previousMember.changeStock(new BigDecimal("275.00"));
        previousMember.completeLearning(
                false,
                LocalDate.of(2026, 10, 29)
        );
        previousMember.completeTutorial();

        memberRepository.saveAndFlush(previousMember);

        // when: 기존 회원이 탈퇴한다.
        memberService.withdraw(previousMemberId);

        Member withdrawnMember = memberRepository.findById(previousMemberId)
                .orElseThrow();

        // then: 과거 회원 행은 탈퇴 상태로 보존하고 식별정보만 해제한다.
        assertThat(withdrawnMember.getStatus())
                .isEqualTo(MemberStatus.WITHDRAWN);
        assertThat(withdrawnMember.getDeletedAt())
                .isEqualTo(NOW_LOCAL);
        assertThat(withdrawnMember.getKakaoUserId()).isNull();
        assertThat(withdrawnMember.getNickname()).isNull();

        // when: 같은 카카오 계정과 닉네임으로 다시 가입한다.
        Member rejoinedMember = memberService.signup(
                KAKAO_USER_ID,
                NICKNAME
        );

        // then: 새로운 회원 행이 생성된다.
        assertThat(rejoinedMember.getMemberId())
                .isNotEqualTo(previousMemberId);
        assertThat(rejoinedMember.getKakaoUserId())
                .isEqualTo(KAKAO_USER_ID);
        assertThat(rejoinedMember.getNickname())
                .isEqualTo(NICKNAME);
        assertThat(rejoinedMember.getStatus())
                .isEqualTo(MemberStatus.ACTIVE);

        // 기존 회원의 자산과 학습 기록은 승계하지 않는다.
        assertThat(rejoinedMember.getFishBalance()).isZero();
        assertThat(rejoinedMember.getCurrentStock())
                .isEqualByComparingTo("100.00");
        assertThat(rejoinedMember.getCurrentStreak()).isZero();
        assertThat(rejoinedMember.getLastLearningDate()).isNull();
        assertThat(rejoinedMember.getLastAttendanceRewardDate()).isNull();
        assertThat(rejoinedMember.getTutorialCompleted()).isFalse();
        assertThat(rejoinedMember.getDeletedAt()).isNull();

        // 기존 탈퇴 행과 신규 가입 행이 모두 보존된다.
        assertThat(memberRepository.findById(previousMemberId))
                .isPresent();
        assertThat(memberRepository.findByKakaoUserId(KAKAO_USER_ID))
                .get()
                .extracting(Member::getMemberId)
                .isEqualTo(rejoinedMember.getMemberId());
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, KST);
        }
    }
}
