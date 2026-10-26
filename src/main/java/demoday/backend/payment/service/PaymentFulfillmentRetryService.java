package demoday.backend.payment.service;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentFulfillmentRetryService {

    private final PaymentRepository paymentRepository;
    private final PaymentProcessingTransactionService transactionService;

    public void retryPendingFulfillments() {
        List<Long> paymentIds = paymentRepository.findIdsByStatus(
                PaymentStatus.APPROVED
        );

        for (Long paymentId : paymentIds) {
            try {
                // 각 지급 건은 transactionService에서 독립된 트랜잭션으로 처리한다.
                transactionService.fulfill(paymentId);
            } catch (RuntimeException exception) {
                transactionService.recordFulfillmentFailure(
                        paymentId,
                        "패스 지급 재처리에 실패했습니다."
                );

                log.warn(
                        "[Payment] 상품 지급 재처리 실패 - paymentId: {}",
                        paymentId
                );
            }
        }
    }
}
