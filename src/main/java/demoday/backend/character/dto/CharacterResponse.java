package demoday.backend.character.dto;

import demoday.backend.character.code.CharacterEffect;
import demoday.backend.character.code.CharacterStage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CharacterResponse(
        @Schema(description = "조회 기준 날짜 (KST)") LocalDate date,
        BigDecimal currentStock,
        @Schema(description = "현재 주가로 결정한 외형: BEGINNER, BIG_HAND, NOBLE") CharacterStage stage,
        String stageName,
        @Schema(description = "다음 외형. 귀족냥이면 null") CharacterStage nextStage,
        @Schema(description = "다음 외형에 필요한 주가. 귀족냥이면 null") BigDecimal nextStageStock,
        @Schema(description = "다음 외형까지 부족한 주가. 귀족냥이면 null") BigDecimal stockToNextStage,
        @Schema(description = "현재 유효한 연속 학습일. 끊겼으면 0") int currentStreak,
        @Schema(description = "홈·모드 선택 화면에 적용할 효과. 현재는 가장 높은 효과 하나, 3일 미만이면 빈 배열") List<CharacterEffect> effects,
        @Schema(description = "다음 스트릭 효과. 21일 이상이면 null") CharacterEffect nextEffect,
        @Schema(description = "다음 효과까지 필요한 학습일. 21일 이상이면 null") Integer daysToNextEffect
) {}
