package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.PayAppClient;
import demoday.backend.payment.client.dto.PayAppCancelResponse;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.dto.PaymentCancelResponse;
import demoday.backend.payment.logging.PaymentLogSupport;
import demoday.backend.payment.metric.PaymentMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "app.payment",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class PaymentCancellationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String CANCELLATION_MEMO =
            "고객 요청에 따른 전액 취소";

    private final PaymentCancellationTransactionService transactionService;
    private final PayAppClient payAppClient;
    private final PaymentMetrics paymentMetrics;
    private final Clock clock;

    public PaymentCancelResponse cancel(
            Long memberId,
            Long paymentId
    ) {
        try {
            return paymentMetrics.recordCancellationProcessing(
                    () -> cancelInternal(memberId, paymentId)
            );
        } catch (ProjectException exception) {
            if (exception.getErrorCode().getStatus().is4xxClientError()) {
                paymentMetrics.recordCancellationRejected();
            } else {
                paymentMetrics.recordCancellationFailure();
            }
            throw exception;
        } catch (RuntimeException exception) {
            paymentMetrics.recordCancellationFailure();
            throw exception;
        }
    }

    private PaymentCancelResponse cancelInternal(
            Long memberId,
            Long paymentId
    ) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        if (paymentId == null || paymentId <= 0) {
            throw new ProjectException(
                    GeneralErrorCode.BAD_REQUEST
            );
        }

        LocalDateTime now = now();

        var preparation = transactionService.prepare(
                memberId,
                paymentId,
                now
        );

        if (preparation.alreadyRefunded()) {
            return preparation.existingResult();
        }

        PayAppCancelResponse payAppResponse;

        try {
            payAppResponse = payAppClient.cancelPayment(
                    preparation.providerTransactionId(),
                    CANCELLATION_MEMO
            );
        } catch (ProjectException exception) {
            throw exception;
        }

        if (!payAppResponse.success()) {
            transactionService.restoreAfterRejection(
                    preparation.paymentId(),
                    preparation.previousStatus(),
                    "페이앱 결제 취소 요청이 거절되었습니다."
            );

            throw new ProjectException(
                    PaymentErrorCode.PAYAPP_CANCEL_FAILED
            );
        }

        PaymentCancelResponse response =
                transactionService.completeRefund(
                        preparation.paymentId(),
                        now()
                );

        paymentMetrics.recordCancellationSuccess();

        log.info(
                "[Payment] 결제 전액 환불 완료 - paymentId: {}, orderNumber: {}, memberId: {}, transactionId: {}, amount: {}, status: {}",
                response.paymentId(),
                response.orderNumber(),
                memberId,
                PaymentLogSupport.maskTransactionId(
                        preparation.providerTransactionId()
                ),
                response.refundedAmount(),
                response.status()
        );

        return response;
    }

    public void cancelLateApprovedPayment(
            Long paymentId,
            String providerTransactionId
    ) {
        PayAppCancelResponse response = payAppClient.cancelPayment(
                providerTransactionId,
                "만료된 주문의 지연 승인 자동 취소"
        );

        if (!response.success()) {
            log.error(
                    "[Payment] 지연 승인 자동 취소 실패 - paymentId: {}, transactionId: {}, errorCode: {}",
                    paymentId,
                    PaymentLogSupport.maskTransactionId(
                            providerTransactionId
                    ),
                    response.errorCode()
            );
            throw new ProjectException(
                    PaymentErrorCode.PAYAPP_CANCEL_FAILED
            );
        }

        transactionService.completeRefund(paymentId, now());

        log.info(
                "[Payment] 지연 승인 자동 환불 완료 - paymentId: {}, transactionId: {}",
                paymentId,
                PaymentLogSupport.maskTransactionId(
                        providerTransactionId
                )
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(KST))
                .truncatedTo(ChronoUnit.MICROS);
    }
}
