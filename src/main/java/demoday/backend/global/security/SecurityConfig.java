package demoday.backend.global.security;

import demoday.backend.auth.service.KakaoAuthService;
import demoday.backend.member.domain.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import java.util.List;
import java.util.Optional;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            KakaoAuthService kakaoAuthService,
            @Value("${app.frontend-base-url}") String frontendBaseUrl
    ) throws Exception {

        HttpSessionSecurityContextRepository contextRepository =
                new HttpSessionSecurityContextRepository();

        return http
                .securityContext(context -> context
                        .securityContextRepository(contextRepository))
                .formLogin(formLogin -> formLogin.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/health",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/api/v1/members/nickname-availability"
                        ).permitAll()
                        .anyRequest().hasRole("MEMBER"))
                .oauth2Login(oauth2 -> oauth2
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri("/api/v1/auth"))
                        .redirectionEndpoint(redirection -> redirection
                                .baseUri("/api/v1/auth/kakao/callback"))
                        .successHandler((request, response, authentication) -> {
                            OAuth2User kakaoUser =
                                    (OAuth2User) authentication.getPrincipal();

                            Long kakaoUserId =
                                    kakaoAuthService.extractKakaoUserId(kakaoUser);

                            Optional<Member> member =
                                    kakaoAuthService.findMemberByKakaoUserId(kakaoUserId);

                            if (member.isEmpty()) {
                                SecurityContextHolder.clearContext();
                                request.getSession().removeAttribute(
                                        HttpSessionSecurityContextRepository
                                                .SPRING_SECURITY_CONTEXT_KEY
                                );
                                request.getSession().setAttribute(
                                        KakaoAuthService.PENDING_KAKAO_USER_ID,
                                        kakaoUserId
                                );

                                response.sendRedirect(frontendBaseUrl + "/signup");
                                return;
                            }

                            request.getSession().removeAttribute(
                                    KakaoAuthService.PENDING_KAKAO_USER_ID
                            );

                            var memberAuthentication =
                                    new UsernamePasswordAuthenticationToken(
                                            member.get().getMemberId().toString(),
                                            null,
                                            List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
                                    );

                            SecurityContext context =
                                    SecurityContextHolder.createEmptyContext();
                            context.setAuthentication(memberAuthentication);
                            SecurityContextHolder.setContext(context);
                            contextRepository.saveContext(context, request, response);

                            response.sendRedirect(frontendBaseUrl + "/");
                        }))
                .build();
    }
}
