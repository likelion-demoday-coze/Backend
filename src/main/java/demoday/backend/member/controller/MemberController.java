package demoday.backend.member.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.member.dto.MemberResponse;
import demoday.backend.member.dto.NicknameAvailabilityResponse;
import demoday.backend.member.dto.NicknameUpdateRequest;
import demoday.backend.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member", description = "회원 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "닉네임 사용 가능 여부 확인")
    @GetMapping("/nickname-availability")
    public ApiResponse<NicknameAvailabilityResponse> checkNickname(
            @RequestParam
            @NotBlank
            @Size(max = 8)
            String nickname
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                memberService.checkNickname(nickname)
        );
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<MemberResponse> getMyProfile(
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                memberService.getMyProfile(memberId)
        );
    }

    @Operation(
            summary = "닉네임 변경",
            description = "로그인 회원의 닉네임을 변경합니다. CSRF 토큰이 필요합니다."
    )
    @PatchMapping("/me/nickname")
    public ApiResponse<MemberResponse> updateNickname(
            @AuthenticationPrincipal Long memberId,
            @Valid @RequestBody NicknameUpdateRequest request
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                memberService.updateNickname(memberId, request)
        );
    }

    @Operation(
            summary = "회원 탈퇴",
            description = "로그인 회원을 탈퇴 처리합니다. CSRF 토큰이 필요합니다."
    )
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(
            @AuthenticationPrincipal Long memberId,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication auth
    ) {
        memberService.withdraw(memberId);

        new SecurityContextLogoutHandler().logout(request, response, auth);

        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "최초 튜토리얼 완료",
            description = "로그인 회원의 최초 튜토리얼을 완료 처리합니다. 여러 번 호출해도 동일하게 성공합니다."
    )
    @PatchMapping("/me/tutorial")
    public ApiResponse<MemberResponse> completeTutorial(
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                memberService.completeTutorial(memberId)
        );
    }
}