package demoday.backend.auth.controller;

import demoday.backend.auth.dto.SignupRequest;
import demoday.backend.auth.service.KakaoAuthService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.dto.MemberResponse;
import demoday.backend.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Auth", description = "인증 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final MemberService memberService;
    private final HttpSessionSecurityContextRepository contextRepository;

    @Operation(summary = "CSRF 토큰 조회")
    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken csrfToken) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                Map.of(
                        "headerName", csrfToken.getHeaderName(),
                        "token", csrfToken.getToken()
                )
        );
    }

    @Operation(summary = "카카오 신규 회원가입")
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<MemberResponse>> signup(
            @Valid @RequestBody SignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        HttpSession session = request.getSession(false);

        if (session == null
                || !(session.getAttribute(KakaoAuthService.PENDING_KAKAO_USER_ID)
                instanceof Long kakaoUserId)) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }

        Member member = memberService.signup(
                kakaoUserId,
                signupRequest.nickname()
        );

        session.removeAttribute(KakaoAuthService.PENDING_KAKAO_USER_ID);
        request.changeSessionId();

        var authentication = new UsernamePasswordAuthenticationToken(
                member.getMemberId().toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER"))
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(
                        GeneralSuccessCode.CREATED,
                        MemberResponse.from(member)
                ));
    }
}
