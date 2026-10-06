package demoday.backend.payment.service;

import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassType;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.repository.MemberPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentFulfillmentService {

    private final MemberRepository members;
    private final MemberPassRepository memberPasses;
    private final FishService fishService;

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

        switch (payment.getOrderedProductType()) {
            case FISH -> issueFish(payment);
            case PASS -> issuePass(payment, approvedAt);
            default -> throw new ProjectException(
                    PaymentErrorCode.FULFILLMENT_FAILED
            );
        }
    }

    private void issueFish(Payment payment) {
        Integer fishAmount = payment.getOrderedFishAmount();

        if (fishAmount == null || fishAmount <= 0) {
            throw new ProjectException(
                    PaymentErrorCode.FULFILLMENT_FAILED
            );
        }

        // paymentId를 멱등키와 참조 ID로 사용, 같은 승인 콜백이 다시 처리되어도 생선이 중복 적립되지 않음
        fishService.credit(
                payment.getMember().getMemberId(),
                fishAmount.longValue(),
                FishTransactionType.PAID_CHARGE,
                payment.getPaymentId(),
                createFishIdempotencyKey(payment)
        );
    }

    private void issuePass(
            Payment payment,
            LocalDateTime approvedAt
    ) {
        Long memberId = payment.getMember().getMemberId();

        // 서로 다른 결제 주문이 동시에 승인되는 경우에도 동일 회원에게 패스가 두 번 발급되지 않도록 회원 행을 잠금
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );

        // 일주일 패스는 회원당 평생 한 번만 구매 가능
        if (memberPasses.existsByMemberMemberId(memberId)) {
            throw new ProjectException(
                    PaymentErrorCode.PASS_ALREADY_PURCHASED
            );
        }

        Integer durationHours = payment.getOrderedPassDurationHours();

        if (durationHours == null || durationHours <= 0) {
            throw new ProjectException(
                    PaymentErrorCode.FULFILLMENT_FAILED
            );
        }

        // 서버 콜백 처리 시각이 아니라 코페이가 반환한 실제 승인 시각부터 상품에 설정된 시간만큼 패스를 활성화
        MemberPass memberPass = MemberPass.create(
                member,
                payment,
                PassType.SEVEN_DAY,
                approvedAt,
                approvedAt.plusHours(durationHours)
        );

        memberPasses.save(memberPass);
    }

    private String createFishIdempotencyKey(
            Payment payment
    ) {
        return "PAYMENT:FISH:"
                + payment.getPaymentId();
    }
}
