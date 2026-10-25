package demoday.backend.payment.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.payment.dto.CurrentMemberPassResponse;
import demoday.backend.payment.dto.MemberPassHistoryPageResponse;
import demoday.backend.payment.service.MemberPassQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Pass", description = "일주일 패스 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/passes/me")
public class MemberPassController {

    private final MemberPassQueryService memberPassQueryService;

    @Operation(
            summary = "내 현재 패스 조회",
            description = "현재 시각에 사용할 수 있는 패스를 조회합니다. 활성 패스가 없으면 active=false, pass=null을 반환합니다."
    )
    @GetMapping("/current")
    public ApiResponse<CurrentMemberPassResponse> getCurrentPass(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                memberPassQueryService.getCurrentPass(memberId)
        );
    }

    @Operation(
            summary = "내 패스 이용 내역 조회",
            description = "패스 이용 내역을 시작 시각 내림차순으로 조회합니다. 저장 상태가 ACTIVE여도 만료 시각이 지났으면 EXPIRED로 반환합니다."
    )
    @GetMapping
    public ApiResponse<MemberPassHistoryPageResponse> getHistories(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @PageableDefault(size = 20)
            Pageable pageable
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                memberPassQueryService.getHistories(
                        memberId,
                        pageable
                )
        );
    }
}
