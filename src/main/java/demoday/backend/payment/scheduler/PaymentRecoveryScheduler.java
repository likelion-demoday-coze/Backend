package demoday.backend.payment.scheduler;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.repository.PaymentRepository;
import demoday.backend.payment.service.PaymentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRecoveryScheduler {

    private final PaymentProcessingService paymentProcessingService;
    private final PaymentRepository paymentRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        recover("서버 시작");
    }

    @Scheduled(
            cron = "${app.payment.fulfillment-retry-cron:0 */5 * * * *}",
            zone = "Asia/Seoul"
    )
    public void recoverPeriodically() {
        recover("주기 실행");
    }

    private void recover(String trigger) {
        // 승인 요청 직후 서버가 중단된 건은 재승인하지 않고 PG 조회 대상으로 전환한다.
        paymentProcessingService.markStaleConfirmationsUnknown();
        paymentProcessingService.retryPendingFulfillments();

        long unknownCount = paymentRepository.countByStatus(
                PaymentStatus.UNKNOWN
        );
        if (unknownCount > 0) {
            // UNKNOWN은 PG 거래 조회 없이 임의로 재승인하거나 실패 처리하면 안 된다.
            log.warn(
                    "[Payment] 승인 결과 수동 확인 필요 - trigger: {}, count: {}",
                    trigger,
                    unknownCount
            );
        }
    }
}
