package demoday.backend.character.controller;

import demoday.backend.character.dto.CharacterResponse;
import demoday.backend.character.service.CharacterService;
import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Character", description = "현재 주가·연속 학습에 따른 캐릭터 외형과 효과")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/characters")
public class CharacterController {
    private final CharacterService characters;

    @Operation(summary = "내 캐릭터 상태 조회", description = "큰손냥은 주가 1000 이상, 귀족냥은 10000 이상입니다. 현재 값으로 계산하므로 하락·복구 시 단계가 함께 바뀝니다. 효과는 유효 스트릭 3·7·14·21일 중 최고 단계 하나만 반환합니다. 홈·문제풀이 모드 선택창에서 사용하며 문제풀이 중 효과 표시와 표정·진화 애니메이션은 프론트가 처리합니다. 다음 성장 기준도 반환하고 조회로 DB 상태를 변경하지 않습니다. 세션 인증과 ROLE_MEMBER가 필요합니다.")
    @GetMapping("/me")
    public ApiResponse<CharacterResponse> getCurrent(
            @Parameter(hidden = true) @AuthenticationPrincipal Long memberId) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, characters.getCurrent(memberId));
    }
}
