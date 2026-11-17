package demoday.backend.member.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberWithdrawalService {

    private static final ZoneId KST =
            ZoneId.of("Asia/Seoul");

    private static final long PAYMENT_AUTHENTICATION_MINUTES =
            30L;

    private static final List<PaymentStatus>
            WITHDRAWAL_BLOCKING_PAYMENT_STATUSES =
            List.of(
                    PaymentStatus.APPROVED,
                    PaymentStatus.CANCEL_REQUESTED
            );

    private static final List<PaymentStatus>
            EXPIRABLE_PAYMENT_STATUSES =
            List.of(
                    PaymentStatus.READY,
                    PaymentStatus.PAYMENT_REQUESTED
            );

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final MemberPassRepository memberPassRepository;
    private final Clock clock;

    @Transactional
    public void withdraw(Long memberId) {
        validateMemberId(memberId);

        Member member = findMemberForUpdate(memberId);

        validateNotWithdrawn(member);

        LocalDateTime now = LocalDateTime
                .now(clock.withZone(KST))
                .truncatedTo(ChronoUnit.MICROS);

        validateNoBlockingPayment(memberId, now);
        validateNoActivePass(memberId, now);

        member.withdraw(now);
    }

    private void validateMemberId(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }
    }

    private Member findMemberForUpdate(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );
    }

    private void validateNotWithdrawn(Member member) {
        if (member.isWithdrawn()) {
            throw new ProjectException(
                    MemberErrorCode.ALREADY_WITHDRAWN
            );
        }
    }

    private void validateNoBlockingPayment(
            Long memberId,
            LocalDateTime now
    ) {
        LocalDateTime paymentExpirationBoundary =
                now.minusMinutes(
                        PAYMENT_AUTHENTICATION_MINUTES
                );

        boolean blockingPaymentExists =
                paymentRepository
                        .existsBlockingWithdrawalPayment(
                                memberId,
                                WITHDRAWAL_BLOCKING_PAYMENT_STATUSES,
                                EXPIRABLE_PAYMENT_STATUSES,
                                paymentExpirationBoundary
                        );

        if (blockingPaymentExists) {
            throw new ProjectException(
                    MemberErrorCode
                            .WITHDRAWAL_BLOCKED_BY_PENDING_PAYMENT
            );
        }
    }

    private void validateNoActivePass(
            Long memberId,
            LocalDateTime now
    ) {
        boolean activePassExists =
                memberPassRepository.findActivePass(
                        memberId,
                        PassStatus.ACTIVE,
                        now
                ).isPresent();

        if (activePassExists) {
            throw new ProjectException(
                    MemberErrorCode
                            .WITHDRAWAL_BLOCKED_BY_ACTIVE_PASS
            );
        }
    }
}
