package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.config.PayAppProperties;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.dto.PayAppFeedbackRequest;
import demoday.backend.payment.logging.PaymentLogSupport;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayAppFeedbackService {

    private static final String COMPLETED_STATE = "4";
    private static final String CARD_PAY_TYPE = "1";
    private static final String SUCCESS_RESULT_CODE = "4";
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final List<DateTimeFormatter> APPROVED_AT_FORMATTERS =
            List.of(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );

    private final PayAppProperties properties;
    private final PaymentRepository paymentRepository;
    private final PaymentFulfillmentService fulfillmentService;
    private final Clock clock;

    @Transactional
    public void process(PayAppFeedbackRequest request) {
        validateRequiredValues(request);
        validateAuthentication(request);

        // 결제 요청·대기·취소 상태는 이후 취소 처리 범위에서 다룬다.
        // 현재 상품 지급은 결제 완료(4) 통보에만 반응한다.
        if (!COMPLETED_STATE.equals(request.payState())) {
            log.info(
                    "[Payment] 비완료 Feedback 확인 - orderNumber: {}, transactionId: {}, payState: {}",
                    request.orderNumber(),
                    PaymentLogSupport.maskTransactionId(
                            request.transactionId()
                    ),
                    request.payState()
            );
            return;
        }

        Payment payment = paymentRepository
                .findByOrderNumberForUpdate(request.orderNumber())
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.ORDER_NOT_FOUND
                ));

        validatePaymentInformation(payment, request);

        // 동일 통보가 반복되어도 검증은 수행하되 상품은 다시 지급하지 않는다.
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info(
                    "[Payment] 중복 Feedback 멱등 처리 - paymentId: {}, orderNumber: {}, transactionId: {}",
                    payment.getPaymentId(),
                    payment.getOrderNumber(),
                    PaymentLogSupport.maskTransactionId(
                            request.transactionId()
                    )
            );
            return;
        }

        LocalDateTime feedbackApprovedAt = parseApprovedAt(
                request.approvedAt()
        );

        LocalDateTime approvedAt = payment.getApprovedAt() == null
                ? feedbackApprovedAt
                : payment.getApprovedAt();

        if (payment.getStatus() != PaymentStatus.APPROVED) {
            if (payment.getStatus() != PaymentStatus.READY
                    && payment.getStatus()
                    != PaymentStatus.PAYMENT_REQUESTED) {
                throw new ProjectException(
                        PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
                );
            }

            payment.approve(
                    request.transactionId(),
                    SUCCESS_RESULT_CODE,
                    "CARD",
                    approvedAt
            );
        }

        fulfillmentService.fulfill(payment, approvedAt);
        payment.complete(
                LocalDateTime.now(clock.withZone(KST))
                        .truncatedTo(ChronoUnit.MICROS)
        );

        log.info(
                "[Payment] Feedback 승인 및 상품 지급 완료 - paymentId: {}, orderNumber: {}, memberId: {}, transactionId: {}, amount: {}, status: {}",
                payment.getPaymentId(),
                payment.getOrderNumber(),
                payment.getMember().getMemberId(),
                PaymentLogSupport.maskTransactionId(
                        request.transactionId()
                ),
                payment.getAmount(),
                payment.getStatus()
        );
    }

    private void validateRequiredValues(
            PayAppFeedbackRequest request
    ) {
        if (request == null
                || isBlank(request.userId())
                || isBlank(request.linkKey())
                || isBlank(request.linkValue())
                || isBlank(request.price())
                || isBlank(request.payState())
                || isBlank(request.orderNumber())
                || isBlank(request.transactionId())) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_FEEDBACK
            );
        }

        if (COMPLETED_STATE.equals(request.payState())
                && (isBlank(request.productName())
                || isBlank(request.approvedAt())
                || !CARD_PAY_TYPE.equals(request.payType()))) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_FEEDBACK
            );
        }
    }

    private void validateAuthentication(
            PayAppFeedbackRequest request
    ) {
        if (!constantTimeEquals(
                properties.userId(),
                request.userId()
        ) || !constantTimeEquals(
                properties.linkKey(),
                request.linkKey()
        ) || !constantTimeEquals(
                properties.linkValue(),
                request.linkValue()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_FEEDBACK_AUTHENTICATION
            );
        }
    }

    private void validatePaymentInformation(
            Payment payment,
            PayAppFeedbackRequest request
    ) {
        int amount;

        try {
            amount = Integer.parseInt(request.price());
        } catch (NumberFormatException exception) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_FEEDBACK
            );
        }

        boolean transactionMatches =
                payment.getProviderTransactionId() == null
                || payment.getProviderTransactionId().equals(
                request.transactionId()
        );

        if (!transactionMatches
                || payment.getAmount() != amount
                || !payment.getOrderedProductName().equals(
                request.productName()
        )) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_INFORMATION_MISMATCH
            );
        }
    }

    private LocalDateTime parseApprovedAt(String value) {
        for (DateTimeFormatter formatter : APPROVED_AT_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 페이앱 환경별 지원 형식을 순서대로 확인한다.
            }
        }

        throw new ProjectException(
                PaymentErrorCode.INVALID_FEEDBACK
        );
    }

    private boolean constantTimeEquals(
            String expected,
            String actual
    ) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
