package demoday.backend.member.dto;

import demoday.backend.character.code.CharacterStage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "마이페이지 조회 응답")
public record MyPageResponse(

        @Schema(description = "회원 기본 정보")
        Profile profile,

        @Schema(description = "보유 자산")
        Assets assets,

        @Schema(description = "회원 학습 기록")
        Records records
) {

    @Schema(description = "회원 기본 정보")
    public record Profile(

            @Schema(description = "회원 ID", example = "1")
            Long memberId,

            @Schema(description = "닉네임", example = "주식왕해윤")
            String nickname,

            @Schema(description = "가입 후 경과 일수", example = "12")
            Long joinedDays,

            @Schema(description = "캐릭터 단계", example = "BEGINNER")
            CharacterStage characterStage,

            @Schema(description = "패스 활성 여부", example = "true")
            Boolean passActive,

            @Schema(
                    description = "패스 만료 시각. 활성 패스가 없으면 null",
                    nullable = true
            )
            LocalDateTime passExpiresAt
    ) {
    }

    @Schema(description = "보유 자산")
    public record Assets(

            @Schema(description = "보유 생선", example = "300")
            Long fishBalance,

            @Schema(description = "보유 연속 학습 복구권", example = "2")
            Integer recoveryTicketCount,

            @Schema(description = "현재 주가", example = "275.00")
            BigDecimal currentStock
    ) {
    }

    @Schema(description = "회원 학습 기록")
    public record Records(

            @Schema(description = "현재 연속 학습일", example = "7")
            Integer currentStreak,

            @Schema(description = "최고 연속 학습일", example = "12")
            Integer highestStreak,

            @Schema(description = "최고 주가", example = "275.00")
            BigDecimal highestStock,

            @Schema(description = "타임어택 최고 정답 수", example = "22")
            Integer highestTimeAttackCorrectCount,

            @Schema(description = "누적 문제 풀이 수", example = "135")
            Long totalAnsweredCount,

            @Schema(description = "누적 정답 수", example = "92")
            Long totalCorrectCount,

            @Schema(description = "누적 정답률", example = "68.15")
            BigDecimal accuracyPercent
    ) {
    }
}
