package demoday.backend.character.dto;

import demoday.backend.character.code.CharacterStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CharacterEvolutionConfirmRequest(

        @NotNull
        @Schema(
                description = """
                        확인한 진화 단계:
                        BASIC, BIG_HAND, ROYAL
                        """,
                example = "BIG_HAND"
        )
        CharacterStage stage
) {
}
