package demoday.backend.character.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CharacterErrorCode implements BaseErrorCode {

    INACTIVE_MEMBER(
            HttpStatus.FORBIDDEN,
            "CHARACTER_403_1",
            "탈퇴한 회원은 캐릭터 상태를 조회할 수 없습니다."
    ),

    EVOLUTION_STAGE_MISMATCH(
            HttpStatus.CONFLICT,
            "CHARACTER_409_1",
            "현재 확인할 수 있는 캐릭터 진화 단계가 아닙니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
