package demoday.backend.character.dto;

import demoday.backend.character.code.CharacterEffect;
import demoday.backend.character.code.CharacterStage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CharacterResponse(

        @Schema(description = "조회 기준 날짜 (KST)")
        LocalDate date,

        BigDecimal currentStock,

        @Schema(
                description = """
                        현재 캐릭터 단계:
                        BABY, BASIC, BIG_HAND, ROYAL
                        """
        )
        CharacterStage stage,

        String stageName,

        @Schema(
                description = "다음 캐릭터 단계. 왕족냥이면 null"
        )
        CharacterStage nextStage,

        @Schema(
                description = """
                        다음 단계에 필요한 주가.
                        아기냥은 정답 1개로 진화하므로 null.
                        왕족냥도 다음 단계가 없어 null.
                        """
        )
        BigDecimal nextStageStock,

        @Schema(
                description = """
                        다음 단계까지 부족한 주가.
                        아기냥과 왕족냥은 null.
                        """
        )
        BigDecimal stockToNextStage,

        @Schema(
                description = """
                        다음 단계까지 필요한 정답 수.
                        아기냥이면 1, 나머지는 null.
                        """
        )
        Integer correctAnswersToNextStage,

        @Schema(
                description = "현재 유효한 연속 학습일"
        )
        int currentStreak,

        @Schema(
                description = """
                        홈·모드 선택 화면에 적용할
                        현재 최고 연속 학습 효과.
                        2·3·5·7·10·14·17·21일 중 달성한 최고 단계 하나를 반환한다.
                        """
        )
        List<CharacterEffect> effects,

        @Schema(
                description = """
                        다음 스트릭 효과.
                        마지막 효과까지 달성했다면 null.
                        """
        )
        CharacterEffect nextEffect,

        @Schema(
                description = """
                        다음 효과까지 필요한 학습일.
                        마지막 효과까지 달성했다면 null.
                        """
        )
        Integer daysToNextEffect,

        @Schema(
                description = """
                        사용자가 아직 확인하지 않은 진화 단계.
                        진화 팝업이 없으면 null.
                        """
        )
        CharacterStage pendingEvolutionStage
) {
}
