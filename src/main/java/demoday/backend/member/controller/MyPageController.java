package demoday.backend.member.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.member.dto.MyPageResponse;
import demoday.backend.member.service.MyPageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "My Page",
        description = "마이페이지 API"
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/me/mypage")
public class MyPageController {

    private final MyPageService myPageService;

    @Operation(
            summary = "마이페이지 요약 조회",
            description = """
                    로그인 회원의 프로필, 보유 자산 및 학습 기록 요약을 조회합니다.
                    세션 인증이 필요합니다.
                    """
    )
    @GetMapping
    public ApiResponse<MyPageResponse> getMyPage(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                myPageService.getMyPage(memberId)
        );
    }
}
