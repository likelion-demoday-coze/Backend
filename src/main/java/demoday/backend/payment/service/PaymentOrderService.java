package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.PayAppClient;
import demoday.backend.payment.client.dto.PayAppPaymentRequest;
import demoday.backend.payment.client.dto.PayAppPaymentResponse;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.dto.PaymentOrderRequest;
import demoday.backend.payment.dto.PaymentOrderResponse;
import demoday.backend.payment.logging.PaymentLogSupport;
import demoday.backend.payment.metric.PaymentMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Slf4j
@Service
@ConditionalOnProperty(
        prefix = "app.payment",
        name = "enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class PaymentOrderService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ORDER_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final PaymentOrderTransactionService transactionService;
    private final PayAppClient payAppClient;
    private final PaymentMetrics paymentMetrics;
    private final Clock clock;

    public PaymentOrderResponse createOrder(
            Long memberId,
            PaymentOrderRequest request
    ) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }

        LocalDateTime requestedAt = LocalDateTime.now(
                clock.withZone(KST)
        ).truncatedTo(ChronoUnit.MICROS);

        String orderNumber = createOrderNumber(requestedAt);

        Payment readyPayment = transactionService.createReadyOrder(
                memberId,
                request.productCode(),
                orderNumber,
                requestedAt
        );

        log.info(
                "[Payment] 결제 주문 생성 완료 - paymentId: {}, orderNumber: {}, memberId: {}, amount: {}",
                readyPayment.getPaymentId(),
                readyPayment.getOrderNumber(),
                memberId,
                readyPayment.getAmount()
        );

        paymentMetrics.recordOrderCreated();

        try {
            PayAppPaymentResponse payAppResponse =
                    payAppClient.requestPayment(
                            new PayAppPaymentRequest(
                                    readyPayment.getOrderNumber(),
                                    readyPayment.getOrderedProductName(),
                                    readyPayment.getAmount(),
                                    request.receiverPhone()
                            )
                    );

            Payment requestedPayment =
                    transactionService.markPaymentRequested(
                            orderNumber,
                            payAppResponse.transactionId()
                    );

            log.info(
                    "[Payment] 결제창 발급 완료 - paymentId: {}, orderNumber: {}, memberId: {}, transactionId: {}, status: {}",
                    requestedPayment.getPaymentId(),
                    requestedPayment.getOrderNumber(),
                    memberId,
                    PaymentLogSupport.maskTransactionId(
                            payAppResponse.transactionId()
                    ),
                    requestedPayment.getStatus()
            );

            return PaymentOrderResponse.of(
                    requestedPayment,
                    payAppResponse.paymentUrl()
            );
        } catch (ProjectException exception) {
            transactionService.markRequestFailed(
                    orderNumber,
                    exception.getErrorCode().getCode(),
                    exception.getErrorCode().getMessage()
            );

            throw exception;
        }
    }

    private String createOrderNumber(LocalDateTime requestedAt) {
        return "PAY"
                + requestedAt.format(ORDER_TIME_FORMAT)
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }
}
