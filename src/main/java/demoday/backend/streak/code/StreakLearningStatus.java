package demoday.backend.streak.code;

/** 날짜별 화면 표시 상태. 복구 완료는 연속성만 연결하며 직접 학습일로 세지 않는다. */
public enum StreakLearningStatus {
    COMPLETED,
    INCOMPLETE,
    RECOVERY_PENDING,
    RECOVERY_COMPLETED
}
