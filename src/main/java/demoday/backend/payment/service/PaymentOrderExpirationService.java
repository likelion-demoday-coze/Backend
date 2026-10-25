package demoday.backend.payment.service;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.config.PaymentOrderPolicyProperties;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderExpirationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final List<PaymentStatus> EXPIRABLE_STATUSES =
            List.of(
                    PaymentStatus.READY,
                    PaymentStatus.PAYMENT_REQUESTED
            );

    private final PaymentRepository paymentRepository;
    private final PaymentOrderExpirationTransactionService
            transactionService;
    private final PaymentOrderPolicyProperties properties;
    private final Clock clock;

    public void expirePendingOrders() {
        LocalDateTime expiredBefore = LocalDateTime.now(
                clock.withZone(KST)
        ).minus(properties.expiration());

        List<Long> paymentIds = paymentRepository.findExpiredOrderIds(
                EXPIRABLE_STATUSES,
                expiredBefore
        );

        long expiredCount = paymentIds.stream()
                .filter(paymentId ->
                        transactionService.expireIfEligible(
                                paymentId,
                                expiredBefore
                        )
                )
                .count();

        if (expiredCount > 0) {
            log.info(
                    "[Payment] 만료 결제 주문 정리 완료 - expiredCount: {}",
                    expiredCount
            );
        }
    }
}
