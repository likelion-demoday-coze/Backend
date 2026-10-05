package demoday.backend.member.domain;

import demoday.backend.attendance.code.AttendanceErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.code.MemberStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    @Column(name = "last_learning_date")
    private LocalDate lastLearningDate;

    @Column(name = "last_attendance_reward_date")
    private LocalDate lastAttendanceRewardDate;

    @Column(name = "tutorial_completed", nullable = false)
    private Boolean tutorialCompleted;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 기존 회원의 실제 가입 시각은 알 수 없으므로 null을 허용한다.
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    private void recordCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(java.time.ZoneId.of("Asia/Seoul"))
                    .truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        }
    }

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

    // 잔액 변경은 거래 이력을 함께 남기는 FishService를 통해 호출한다.
    public void addFish(long amount) {
        if (amount <= 0) {
            throw new ProjectException(MemberErrorCode.INVALID_FISH_AMOUNT);
        }

        if (fishBalance > Long.MAX_VALUE - amount) {
            throw new ProjectException(MemberErrorCode.FISH_BALANCE_OVERFLOW);
        }

        fishBalance += amount;
    }

    // 회원의 생선 잔액 차감
    public void deductFish(long amount) {
        if (amount <= 0) {
            throw new ProjectException(MemberErrorCode.INVALID_FISH_AMOUNT);
        }

        if (fishBalance < amount) {
            throw new ProjectException(MemberErrorCode.INSUFFICIENT_FISH);
        }

        fishBalance -= amount;
    }

    // 정답 맞히고 주가 상승 기회 남아 있을 때 현재 주가 올림
    public void increaseStock(int increasePercent) {
        if (increasePercent < 1 || increasePercent > 10) {
            throw new ProjectException(MemberErrorCode.INVALID_STOCK_INCREASE_PERCENT);
        }

        BigDecimal rate = BigDecimal.ONE.add(
                BigDecimal.valueOf(increasePercent).movePointLeft(2)
        );

        changeStock(currentStock.multiply(rate).setScale(2, RoundingMode.HALF_UP));
    }

    // 업무에서는 주가와 이력을 함께 저장하는 StockService.changeStock을 사용한다.
    public void changeStock(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.TEN.pow(28)) >= 0) {
            throw new ProjectException(MemberErrorCode.INVALID_STOCK_VALUE);
        }
        try {
            currentStock = value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new ProjectException(MemberErrorCode.INVALID_STOCK_VALUE);
        }
    }

    /** 지급과 같은 트랜잭션 안에서 AttendanceRewardService가 호출한다. */
    public void recordAttendanceReward(LocalDate rewardDate) {
        if (rewardDate == null || (lastAttendanceRewardDate != null && !lastAttendanceRewardDate.isBefore(rewardDate))) {
            throw new ProjectException(AttendanceErrorCode.INVALID_REWARD_DATE);
        }
        lastAttendanceRewardDate = rewardDate;
    }

    // 하락 기록을 먼저 저장한 뒤 호출한다. 마지막 완료 날짜는 복구 판정용으로 보존한다.
    public void breakLearningStreak() {
        currentStreak = 0;
    }

    // 연속 학습일 변경
    public void completeLearning(boolean continuedFromYesterday, LocalDate learningDate) {
        if (learningDate == null || (lastLearningDate != null && learningDate.isBefore(lastLearningDate))) {
            throw new IllegalArgumentException("정규장 학습 완료 날짜가 올바르지 않습니다.");
        }
        if (learningDate.equals(lastLearningDate)) return;
        lastLearningDate = learningDate;
        if (continuedFromYesterday) {
            currentStreak++;
            return;
        }

        currentStreak = 1;
    }
}
