package demoday.backend.attendance.service;

import demoday.backend.attendance.code.AttendanceErrorCode;
import demoday.backend.attendance.code.AttendanceRewardStatus;
import demoday.backend.attendance.dto.AttendanceRewardClaimResponse;
import demoday.backend.attendance.dto.AttendanceRewardStatusResponse;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.repository.MemberPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AttendanceRewardService {
    private static final long REWARD_AMOUNT = 100;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private final MemberRepository memberRepository;
    private final MemberPassRepository memberPassRepository;
    private final FishService fishService;
    private final TransactionRetryExecutor retryExecutor;
    private final Clock clock;

    /** 상태 조회는 지급하거나 학습 기록을 변경하지 않는다. */
    @Transactional(readOnly = true)
    public AttendanceRewardStatusResponse getToday(Long memberId) {
        Member member = findMember(memberId, false);
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));
        validateRewardDate(member, now.toLocalDate());
        AttendanceRewardStatus status = hasActivePass(memberId, now)
                ? AttendanceRewardStatus.PASS_ACTIVE
                : now.toLocalDate().equals(member.getLastAttendanceRewardDate())
                        ? AttendanceRewardStatus.CLAIMED
                        : AttendanceRewardStatus.AVAILABLE;
        return new AttendanceRewardStatusResponse(now.toLocalDate(), status, REWARD_AMOUNT);
    }

    /** 가장 바깥 경계에서 수령일·잔액·이력 작업 전체를 새 트랜잭션으로 재시도한다. */
    public AttendanceRewardClaimResponse claim(Long memberId) {
        return retryExecutor.execute(() -> claimInTransaction(memberId));
    }

    private AttendanceRewardClaimResponse claimInTransaction(Long memberId) {
        Member member = findMember(memberId, true);
        // 잠금을 얻은 후의 KST 날짜로 처리한다. 자정 이후 재시도는 새 날짜를 기준으로 한다.
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));
        LocalDate today = now.toLocalDate();
        validateRewardDate(member, today);
        if (today.equals(member.getLastAttendanceRewardDate())) {
            return new AttendanceRewardClaimResponse(today, false, 0, member.getFishBalance());
        }
        if (hasActivePass(memberId, now)) {
            throw new ProjectException(AttendanceErrorCode.ACTIVE_PASS);
        }
        fishService.credit(memberId, REWARD_AMOUNT, FishTransactionType.ATTENDANCE_REWARD,
                null, "ATTENDANCE_REWARD:" + memberId + ":" + today);
        member.recordAttendanceReward(today);
        return new AttendanceRewardClaimResponse(today, true, REWARD_AMOUNT, member.getFishBalance());
    }

    private boolean hasActivePass(Long memberId, LocalDateTime now) {
        return memberPassRepository.findActivePass(memberId, PassStatus.ACTIVE, now).isPresent();
    }

    private Member findMember(Long memberId, boolean forUpdate) {
        if (memberId == null) throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        Member member = (forUpdate ? memberRepository.findByIdForUpdate(memberId) : memberRepository.findById(memberId))
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new ProjectException(AttendanceErrorCode.INACTIVE_MEMBER);
        return member;
    }

    private void validateRewardDate(Member member, LocalDate today) {
        if (member.getLastAttendanceRewardDate() != null && member.getLastAttendanceRewardDate().isAfter(today)) {
            throw new ProjectException(AttendanceErrorCode.INVALID_REWARD_DATE);
        }
    }
}
