package demoday.backend.timeattack.domain;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.timeattack.code.TimeAttackErrorCode;
import demoday.backend.timeattack.code.TimeAttackStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "time_attack_session")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class TimeAttackSession {

    private static final long TIME_LIMIT_SECONDS = 60L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "time_attack_session_id")
    private Long timeAttackSessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Column(name = "attempt_date", nullable = false)
    private LocalDate attemptDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TimeAttackStatus status;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "pass_applied", nullable = false)
    private Boolean passApplied;

    public static TimeAttackSession create(
            Member member,
            LocalDate attemptDate,
            LocalDateTime startedAt,
            Boolean passApplied
    ) {
        return TimeAttackSession.builder()
                .member(member)
                .attemptDate(attemptDate)
                .status(TimeAttackStatus.IN_PROGRESS)
                .correctCount(0)
                .startedAt(startedAt)
                .passApplied(passApplied)
                .build();
    }

    // 종료 예정 시각 계산
    public LocalDateTime getEndsAt() {
        return startedAt.plusSeconds(TIME_LIMIT_SECONDS);
    }

    // 시간 만료 여부 확인
    public boolean isTimeOver(LocalDateTime now) {
        return !now.isBefore(getEndsAt());
    }

    // 답안 제출 가능 여부 검증
    public void validateAnswerable(LocalDateTime now) {
        validateInProgress();

        if (isTimeOver(now)) {
            throw new ProjectException(
                    TimeAttackErrorCode.SESSION_EXPIRED
            );
        }
    }

    // 정답 수 증가
    public void increaseCorrectCount() {
        validateInProgress();
        correctCount++;
    }

    // 정상 완료
    public void complete(LocalDateTime now) {
        validateInProgress();

        if (!isTimeOver(now)) {
            throw new ProjectException(
                    TimeAttackErrorCode.TOO_EARLY_TO_COMPLETE
            );
        }

        status = TimeAttackStatus.COMPLETED;
    }

    // 만료 처리
    public boolean expireIfTimedOut(LocalDateTime now) {
        if (status != TimeAttackStatus.IN_PROGRESS) {
            return false;
        }

        if (!isTimeOver(now)) {
            return false;
        }

        status = TimeAttackStatus.EXPIRED;
        return true;
    }

    // 진행 중 상태인지 검증
    private void validateInProgress() {
        if (status != TimeAttackStatus.IN_PROGRESS) {
            throw new ProjectException(
                    TimeAttackErrorCode.SESSION_NOT_IN_PROGRESS
            );
        }
    }
}
