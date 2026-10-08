package demoday.backend.global.security;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.repository.MemberRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ActiveMemberFilter extends OncePerRequestFilter {

    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long memberId
                && !memberRepository.existsByMemberIdAndStatus(
                        memberId,
                        MemberStatus.ACTIVE
                )) {
            SecurityContextHolder.clearContext();

            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            objectMapper.writeValue(
                    response.getWriter(),
                    ApiResponse.onFailure(
                            GeneralErrorCode.UNAUTHORIZED,
                            null
                    )
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
