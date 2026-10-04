package demoday.backend.streak.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

public record StreakResponse(
        @Schema(description = "조회 기준 날짜 (KST)") LocalDate date,
        @Schema(description = "현재 이어지는 연속 학습일. 마지막 학습이 어제보다 이전이면 0") int currentStreak,
        @Schema(description = "오늘 정규장 원본 5문제를 모두 제출했는지 여부") boolean learnedToday,
        @Schema(description = "마지막 정규장 학습 완료일. 없으면 null") LocalDate lastLearningDate
) {}
