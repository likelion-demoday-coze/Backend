package demoday.backend.member.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.member.dto.NicknameAvailabilityResponse;
import demoday.backend.member.service.MemberService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members")
public class MemberController {

    private final MemberService memberService;

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
}