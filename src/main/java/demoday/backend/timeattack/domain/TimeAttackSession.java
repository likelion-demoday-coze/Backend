package demoday.backend.timeattack.domain;

import demoday.backend.member.domain.Member;
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

    @Column(name = "passed_applied", nullable = false)
    private Boolean passedApplied;

    public static TimeAttackSession create(
            Member member,
            LocalDate attemptDate,
            TimeAttackStatus status,
            LocalDateTime startedAt,
            Boolean passedApplied
    ) {
        return TimeAttackSession.builder()
                .member(member)
                .attemptDate(attemptDate)
                .status(status)
                .correctCount(0)
                .startedAt(startedAt)
                .passedApplied(passedApplied)
                .build();
    }
}
