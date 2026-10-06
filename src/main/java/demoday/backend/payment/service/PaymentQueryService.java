package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.dto.PaymentHistoryPageResponse;
import demoday.backend.payment.dto.PaymentStatusResponse;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;

    public PaymentStatusResponse getStatus(
            Long memberId,
            String orderNumber
    ) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        if (orderNumber == null || orderNumber.isBlank()) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_ORDER_NUMBER
            );
        }

        // 주문을 먼저 조회한 뒤 회원을 비교하지 않고 회원 ID와 주문번호를 조회 조건에 함께 넣음
        // 다른 회원의 주문 존재 여부가 노출되지 않게 함
        Payment payment = paymentRepository
                .findByOrderNumberAndMemberMemberId(
                        orderNumber,
                        memberId
                )
                .orElseThrow(() ->
                        new ProjectException(
                                PaymentErrorCode.ORDER_NOT_FOUND
                        )
                );

        return PaymentStatusResponse.from(payment);
    }

    public PaymentHistoryPageResponse getHistories(
            Long memberId,
            Pageable pageable
    ) {
        validateMember(memberId);

        Page<Payment> payments =
                paymentRepository.findAllByMemberMemberIdOrderByRequestedAtDesc(
                        memberId,
                        pageable
                );

        return PaymentHistoryPageResponse.from(payments);
    }

    public PaymentStatusResponse getDetail(
            Long memberId,
            Long paymentId
    ) {
        validateMember(memberId);

        if (paymentId == null || paymentId <= 0) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }

        Payment payment = paymentRepository
                .findByPaymentIdAndMemberMemberId(paymentId, memberId)
                .orElseThrow(() ->
                        new ProjectException(PaymentErrorCode.ORDER_NOT_FOUND)
                );

        return PaymentStatusResponse.from(payment);
    }

    private void validateMember(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }
    }
}
