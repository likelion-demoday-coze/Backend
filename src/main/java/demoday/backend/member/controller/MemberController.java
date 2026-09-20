package demoday.backend.member.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.dto.MemberResponse;
import demoday.backend.member.dto.NicknameAvailabilityResponse;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.member.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Member", description = "회원 API")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;
    private final MemberRepository memberRepository;

    @Operation(summary = "닉네임 사용 가능 여부 확인")
    @GetMapping("/nickname-availability")
    public ApiResponse<NicknameAvailabilityResponse> checkNickname(
            @RequestParam
            @NotBlank
            @Size(max = 10)
            String nickname
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                memberService.checkNickname(nickname)
        );
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/me")
    public MemberResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));

        return MemberResponse.from(member);
    }
}