package demoday.backend.auth.service;

import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class KakaoAuthService {

    private final MemberRepository memberRepository;
    public static final String PENDING_KAKAO_USER_ID = "PENDING_KAKAO_USER_ID";

    public Long extractKakaoUserId(OAuth2User oAuth2User) {
        Object id = oAuth2User.getAttribute("id");

        if (!(id instanceof Number number)) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_kakao_user_id"),
                    "카카오 사용자 ID를 확인할 수 없습니다."
            );
        }
        return number.longValue();
    }

    public Optional<Member> findMemberByKakaoUserId(Long kakaoUserId) {
        return memberRepository.findByKakaoUserId(kakaoUserId);
    }
}
