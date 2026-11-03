package demoday.backend.character.controller;

import demoday.backend.character.dto.CharacterEvolutionConfirmRequest;
import demoday.backend.character.dto.CharacterResponse;
import demoday.backend.character.service.CharacterService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Character", description = "현재 주가·연속 학습에 따른 캐릭터 외형과 효과")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/characters")
public class CharacterController {
    private final CharacterService characters;

    @Operation(summary = "내 캐릭터 상태 조회", description = "아기냥은 주가 100 미만이며 문제를 하나 맞히면 기본냥으로 진화합니다. 기본냥은 주가 100 이상, 큰손냥은 250 이상, 왕족냥은 1500 이상입니다. 하락·복구 시 저장된 단계도 함께 변경됩니다. pendingEvolutionStage가 있으면 프론트에서 진화 팝업을 표시합니다. 효과는 유효 스트릭 3·7·14·21일 중 최고 단계 하나만 반환합니다. 세션 인증과 ROLE_MEMBER가 필요합니다.")
    @GetMapping("/me")
    public ApiResponse<CharacterResponse> getCurrent(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, characters.getCurrent(memberId));
    }

    @Operation(
            summary = "내 캐릭터 진화 단계 확인",
            description = """
                진화 팝업의 확인 버튼을 누른 뒤 호출합니다.
                조회 응답의 pendingEvolutionStage와
                같은 stage를 보내야 합니다.
                """
    )
    @PatchMapping("/me/evolution")
    public ApiResponse<Void> confirmEvolution(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Valid
            @RequestBody
            CharacterEvolutionConfirmRequest request
    ) {
        characters.confirmEvolution(
                memberId,
                request
        );

        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK
        );
    }
}
