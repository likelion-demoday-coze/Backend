package demoday.backend.member.domain;

import demoday.backend.member.code.MemberStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "member")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "kakao_user_id", nullable = false, unique = true)
    private Long kakaoUserId;

    @Column(nullable = false, unique = true, length = 8)
    private String nickname;

    @Column(name = "fish_balance", nullable = false)
    private Long fishBalance;

    @Column(name = "current_stock", nullable = false, precision = 30, scale = 2)
    private BigDecimal currentStock;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "last_attendance_reward_date")
    private LocalDate lastAttendanceRewardDate;

    @Column(name = "tutorial_completed", nullable = false)
    private Boolean tutorialCompleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Member create(Long kakaoUserId, String nickname) {
        return Member.builder()
                .kakaoUserId(kakaoUserId)
                .nickname(nickname)
                .fishBalance(0L)
                .currentStock(new BigDecimal("100.00"))
                .currentStreak(0)
                .tutorialCompleted(false)
                .status(MemberStatus.ACTIVE)
                .build();
    }
}
