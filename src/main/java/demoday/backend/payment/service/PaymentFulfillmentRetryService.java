package demoday.backend.payment.service;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.metric.PaymentMetrics;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentFulfillmentRetryService {

    private final PaymentRepository paymentRepository;
    private final PaymentProcessingTransactionService transactionService;
    private final PaymentMetrics paymentMetrics;

    public void retryPendingFulfillments() {
        List<Long> paymentIds = paymentRepository.findIdsByStatus(
                PaymentStatus.APPROVED
        );

        for (Long paymentId : paymentIds) {
            try {
                transactionService.fulfill(paymentId);
                paymentMetrics.recordFulfillmentSuccess();
            } catch (RuntimeException exception) {
                paymentMetrics.recordFulfillmentFailure();

                transactionService.recordFulfillmentFailure(
                        paymentId,
                        "패스 지급 재처리에 실패했습니다."
                );
            }
        }
    }
}
