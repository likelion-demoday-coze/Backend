package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.config.PassPolicyProperties;
import demoday.backend.payment.config.PaymentOrderPolicyProperties;
import demoday.backend.payment.config.ServicePeriodProperties;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.domain.Product;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.repository.PaymentRepository;
import demoday.backend.payment.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentOrderTransactionService {

    private static final List<PaymentStatus> EXPIRABLE_STATUSES = List.of(
            PaymentStatus.READY,
            PaymentStatus.PAYMENT_REQUESTED
    );

    private static final List<PaymentStatus> UNRESOLVED_STATUSES = List.of(
            PaymentStatus.APPROVED,
            PaymentStatus.CANCEL_REQUESTED
    );

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final MemberPassRepository memberPassRepository;
    private final ServicePeriodProperties servicePeriodProperties;
    private final PassPolicyProperties passPolicyProperties;
    private final PaymentOrderPolicyProperties orderPolicyProperties;

    @Transactional
    public Payment createReadyOrder(
            Long memberId,
            String productCode,
            String orderNumber,
            LocalDateTime requestedAt
    ) {
        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ProjectException(
                        GeneralErrorCode.NOT_FOUND
                ));

        // 활성 회원 필터 통과 직후 탈퇴가 완료되는 동시 요청도 차단한다.
        if (!member.isActive()) {
            throw new ProjectException(MemberErrorCode.INACTIVE_MEMBER);
        }

        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.PRODUCT_NOT_FOUND
                ));

        if (!servicePeriodProperties.isSalesOpenAt(requestedAt)
                || product.getProductType() != ProductType.PASS
                || !product.isAvailableAt(requestedAt)) {
            throw new ProjectException(
                    PaymentErrorCode.PRODUCT_NOT_AVAILABLE
            );
        }

        if (memberPassRepository.findActivePass(
                memberId,
                PassStatus.ACTIVE,
                requestedAt
        ).isPresent()) {
            throw new ProjectException(
                    PaymentErrorCode.ACTIVE_PASS_EXISTS
            );
        }

        boolean unresolvedPaymentExists = paymentRepository
                .existsByMemberMemberIdAndProductProductTypeAndStatusIn(
                        memberId,
                        ProductType.PASS,
                        UNRESOLVED_STATUSES
                );

        boolean validPendingOrderExists = paymentRepository
                .existsByMemberMemberIdAndProductProductTypeAndStatusInAndRequestedAtGreaterThan(
                        memberId,
                        ProductType.PASS,
                        EXPIRABLE_STATUSES,
                        requestedAt.minus(
                                orderPolicyProperties.expiration()
                        )
                );

        if (unresolvedPaymentExists || validPendingOrderExists) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_IN_PROGRESS
            );
        }

        return paymentRepository.save(
                Payment.create(
                        member,
                        product,
                        orderNumber,
                        product.getPrice(),
                        passPolicyProperties.durationHours(),
                        requestedAt
                )
        );
    }

    @Transactional
    public Payment markPaymentRequested(
            String orderNumber,
            String providerTransactionId
    ) {
        Payment payment = paymentRepository
                .findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        payment.markPaymentRequested(providerTransactionId);
        return payment;
    }

    @Transactional
    public void markRequestFailed(
            String orderNumber,
            String resultCode,
            String failureMessage
    ) {
        paymentRepository.findByOrderNumberForUpdate(orderNumber)
                .filter(payment -> payment.getStatus() == PaymentStatus.READY)
                .ifPresent(payment -> payment.fail(
                        resultCode,
                        failureMessage
                ));
    }
}
