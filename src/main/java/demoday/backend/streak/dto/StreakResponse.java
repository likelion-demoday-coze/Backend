package demoday.backend.streak.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import demoday.backend.streak.code.StreakLearningStatus;

public record StreakResponse(
        @Schema(description = "조회 기준 날짜 (KST)") LocalDate date,
        @Schema(description = "현재 이어지는 연속 학습일. 마지막 학습이 어제보다 이전이면 0") int currentStreak,
        @Schema(description = "오늘 정규장 원본 5문제를 모두 제출했는지 여부") boolean learnedToday,
        @Schema(description = "마지막 정규장 학습 완료일. 없으면 null") LocalDate lastLearningDate,
        @Schema(description = "보존된 학습·복구 기록 기준 개인 최고 연속학습일. 복구한 쉰 날은 일수에 포함하지 않음") int highestStreak,
        @Schema(description = "이번 주 월요일 (KST)") LocalDate weekStart,
        @Schema(description = "이번 주 일요일 (KST)") LocalDate weekEnd,
        @Schema(description = "월~일 순서로 항상 7개. 완료·미완료·복구대기·복구완료 상태") List<Day> days
) {
    public record Day(LocalDate date, StreakLearningStatus status, boolean today, boolean future) {}
}
