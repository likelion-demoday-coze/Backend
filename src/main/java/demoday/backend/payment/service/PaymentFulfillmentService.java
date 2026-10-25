package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.code.PassType;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.repository.MemberPassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFulfillmentService {

    private final MemberRepository memberRepository;
    private final MemberPassRepository memberPassRepository;

    @Transactional
    public void fulfill(
            Payment payment,
            LocalDateTime approvedAt
    ) {
        if (payment == null || approvedAt == null) {
            throw new ProjectException(
                    PaymentErrorCode.FULFILLMENT_FAILED
            );
        }

        issuePass(payment, approvedAt);
    }

    private void issuePass(
            Payment payment,
            LocalDateTime approvedAt
    ) {
        // 동일 결제의 Feedback 또는 재처리가 반복되어도 패스를 중복 지급하지 않는다.
        if (memberPassRepository.findByPaymentPaymentId(
                payment.getPaymentId()
        ).isPresent()) {
            return;
        }

        Long memberId = payment.getMember().getMemberId();

        // 서로 다른 결제의 동시 지급을 직렬화한다.
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ProjectException(
                        GeneralErrorCode.NOT_FOUND
                ));

        if (!member.isActive()) {
            throw new ProjectException(
                    PaymentErrorCode.FULFILLMENT_FAILED
            );
        }

        if (memberPassRepository.findActivePass(
                memberId,
                PassStatus.ACTIVE,
                approvedAt
        ).isPresent()) {
            throw new ProjectException(
                    PaymentErrorCode.ACTIVE_PASS_EXISTS
            );
        }

        Integer durationHours =
                payment.getOrderedPassDurationHours();

        if (durationHours == null || durationHours <= 0) {
            throw new ProjectException(
                    PaymentErrorCode.FULFILLMENT_FAILED
            );
        }

        MemberPass memberPass = memberPassRepository.save(
                MemberPass.create(
                        member,
                        payment,
                        PassType.SEVEN_DAY,
                        approvedAt,
                        approvedAt.plusHours(durationHours)
                )
        );

        log.info(
                "[Payment] 패스 지급 완료 - paymentId: {}, orderNumber: {}, memberId: {}, memberPassId: {}, expiresAt: {}",
                payment.getPaymentId(),
                payment.getOrderNumber(),
                memberId,
                memberPass.getMemberPassId(),
                memberPass.getExpiresAt()
        );
    }
}
