package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.config.PayAppProperties;
import demoday.backend.payment.dto.PayAppFeedbackRequest;
import demoday.backend.payment.logging.PaymentLogSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayAppFeedbackService {

    private static final String COMPLETED_STATE = "4";
    private static final String CARD_PAY_TYPE = "1";

    private static final List<DateTimeFormatter> APPROVED_AT_FORMATTERS =
            List.of(
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    DateTimeFormatter.ofPattern("yyyyMMddHHmmss"),
                    DateTimeFormatter.ISO_LOCAL_DATE_TIME
            );

    private final PayAppProperties properties;
    private final PaymentProcessingTransactionService transactionService;

    public void process(PayAppFeedbackRequest request) {
        validateRequiredValues(request);
        validateAuthentication(request);

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

        LocalDateTime approvedAt =
                parseApprovedAt(request.approvedAt());

        // 승인 저장은 별도 트랜잭션으로 먼저 확정
        // 이후 상품 지급이 실패해도 APPROVED 상태가 유지
        Long paymentId = transactionService.recordApproval(
                request,
                approvedAt
        );

        // 패스 지급과 COMPLETED 전환은 하나의 별도 트랜잭션
        try {
            transactionService.fulfill(paymentId);
        } catch (RuntimeException exception) {
            // 지급 롤백 이후 별도 트랜잭션으로 실패 사유를 보존한다.
            transactionService.recordFulfillmentFailure(
                    paymentId,
                    "Feedback 수신 후 패스 지급에 실패했습니다."
            );
            throw exception;
        }
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

    private LocalDateTime parseApprovedAt(String value) {
        for (DateTimeFormatter formatter : APPROVED_AT_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 지원 형식을 순서대로 확인
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
