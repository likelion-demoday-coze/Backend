package demoday.backend.payment.client;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.dto.PayAppPaymentRequest;
import demoday.backend.payment.client.dto.PayAppPaymentResponse;
import demoday.backend.payment.client.dto.PayAppCancelResponse;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.config.PayAppProperties;
import demoday.backend.payment.logging.PaymentLogSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "app.payment",
        name = "enabled",
        havingValue = "true"
)
public class HttpPayAppClient implements PayAppClient {

    private static final String SUCCESS_STATE = "1";

    private final RestClient restClient;
    private final PayAppProperties properties;

    public HttpPayAppClient(
            @Qualifier("payAppRestClient")
            RestClient restClient,
            PayAppProperties properties
    ) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public PayAppCancelResponse cancelPayment(
            String transactionId,
            String cancellationMemo
    ) {
        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();

        form.add("cmd", "paycancel");
        form.add("userid", properties.userId());
        form.add("linkkey", properties.linkKey());
        form.add("mul_no", transactionId);
        form.add("cancelmemo", cancellationMemo);
        form.add("partcancel", "0");

        try {
            String responseBody = restClient.post()
                    .uri(properties.requestPath())
                    .contentType(
                            MediaType.APPLICATION_FORM_URLENCODED
                    )
                    .body(form)
                    .retrieve()
                    .body(String.class);

            if (responseBody == null || responseBody.isBlank()) {
                throw new ProjectException(
                        PaymentErrorCode.INVALID_PG_RESPONSE
                );
            }

            Map<String, String> values =
                    parseUrlEncodedResponse(responseBody);

            String state = values.get("state");

            if (state == null || state.isBlank()) {
                throw new ProjectException(
                        PaymentErrorCode.INVALID_PG_RESPONSE
                );
            }

            PayAppCancelResponse response = new PayAppCancelResponse(
                    SUCCESS_STATE.equals(state),
                    values.get("errno"),
                    values.get("errorMessage")
            );

            if (!response.success()) {
                log.warn(
                        "[Payment] 페이앱 결제 취소 거절 - transactionId: {}, errorCode: {}",
                        PaymentLogSupport.maskTransactionId(transactionId),
                        normalizeErrorCode(response.errorCode())
                );
            }

            return response;
        } catch (ProjectException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                log.error(
                        "[Payment] 페이앱 결제 취소 시간 초과 - transactionId: {}",
                        PaymentLogSupport.maskTransactionId(transactionId),
                        exception
                );
                throw new ProjectException(
                        PaymentErrorCode.PG_TIMEOUT
                );
            }

            log.error(
                    "[Payment] 페이앱 결제 취소 통신 실패 - transactionId: {}",
                    PaymentLogSupport.maskTransactionId(transactionId),
                    exception
            );
            throw new ProjectException(
                    PaymentErrorCode.PG_COMMUNICATION_FAILED
            );
        } catch (RestClientResponseException exception) {
            log.error(
                    "[Payment] 페이앱 결제 취소 오류 응답 - transactionId: {}, httpStatus: {}",
                    PaymentLogSupport.maskTransactionId(transactionId),
                    exception.getStatusCode(),
                    exception
            );
            throw new ProjectException(
                    PaymentErrorCode.PG_COMMUNICATION_FAILED
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[Payment] 페이앱 결제 취소 응답 처리 실패 - transactionId: {}",
                    PaymentLogSupport.maskTransactionId(transactionId),
                    exception
            );
            throw new ProjectException(
                    PaymentErrorCode.INVALID_PG_RESPONSE
            );
        }
    }

    @Override
    public PayAppPaymentResponse requestPayment(
            PayAppPaymentRequest request
    ) {
        MultiValueMap<String, String> form =
                createPaymentForm(request);

        try {
            String responseBody = restClient.post()
                    .uri(properties.requestPath())
                    .contentType(
                            MediaType.APPLICATION_FORM_URLENCODED
                    )
                    .body(form)
                    .retrieve()
                    .body(String.class);

            PayAppPaymentResponse response =
                    parsePaymentResponse(
                            request.orderNumber(),
                            responseBody
                    );

            if (!response.success()) {
                log.warn(
                        "[Payment] 페이앱 결제 요청 거절 - orderNumber: {}, errorCode: {}",
                        request.orderNumber(),
                        normalizeErrorCode(response.errorCode())
                );

                throw new ProjectException(
                        PaymentErrorCode.PAYAPP_REQUEST_FAILED
                );
            }

            log.info(
                    "[Payment] 페이앱 결제 요청 성공 - orderNumber: {}, transactionId: {}",
                    request.orderNumber(),
                    PaymentLogSupport.maskTransactionId(
                            response.transactionId()
                    )
            );

            return response;
        } catch (ProjectException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            if (isTimeout(exception)) {
                log.error(
                        "[Payment] 페이앱 결제 요청 시간 초과 - orderNumber: {}",
                        request.orderNumber(),
                        exception
                );

                throw new ProjectException(
                        PaymentErrorCode.PG_TIMEOUT
                );
            }

            log.error(
                    "[Payment] 페이앱 API 통신 실패 - orderNumber: {}",
                    request.orderNumber(),
                    exception
            );

            throw new ProjectException(
                    PaymentErrorCode.PG_COMMUNICATION_FAILED
            );
        } catch (RestClientResponseException exception) {
            log.error(
                    "[Payment] 페이앱 API 오류 응답 - orderNumber: {}, httpStatus: {}",
                    request.orderNumber(),
                    exception.getStatusCode(),
                    exception
            );

            throw new ProjectException(
                    PaymentErrorCode.PG_COMMUNICATION_FAILED
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[Payment] 페이앱 응답 처리 실패 - orderNumber: {}",
                    request.orderNumber(),
                    exception
            );

            throw new ProjectException(
                    PaymentErrorCode.INVALID_PG_RESPONSE
            );
        }
    }

    private MultiValueMap<String, String> createPaymentForm(
            PayAppPaymentRequest request
    ) {
        MultiValueMap<String, String> form =
                new LinkedMultiValueMap<>();

        form.add("cmd", "payrequest");
        form.add("userid", properties.userId());
        form.add("goodname", request.productName());
        form.add("price", request.amount().toString());
        form.add("recvphone", request.receiverPhone());

        // 결제 완료 여부는 returnUrl이 아닌 feedbackUrl에서 처리
        form.add("feedbackurl", properties.feedbackUrl());

        // Feedback에서 내부 주문을 찾기 위한 값
        form.add("var1", request.orderNumber());

        // 페이앱 문자 발송은 사용하지 않음
        form.add("smsuse", "n");

        // Feedback 응답이 SUCCESS가 아니면 페이앱이 재시도
        form.add("checkretry", "y");

        form.add("returnurl", createReturnUrl(
                request.orderNumber()
        ));

        form.add(
                "openpaytype",
                properties.openPayType()
        );

        return form;
    }

    private String createReturnUrl(String orderNumber) {
        return UriComponentsBuilder
                .fromUriString(properties.returnUrl())
                .queryParam("orderNumber", orderNumber)
                .build()
                .encode()
                .toUriString();
    }

    private PayAppPaymentResponse parsePaymentResponse(
            String orderNumber,
            String responseBody
    ) {
        if (responseBody == null || responseBody.isBlank()) {
            log.error(
                    "[Payment] 페이앱 빈 응답 - orderNumber: {}",
                    orderNumber
            );

            throw new ProjectException(
                    PaymentErrorCode.INVALID_PG_RESPONSE
            );
        }

        Map<String, String> values =
                parseUrlEncodedResponse(responseBody);

        String state = values.get("state");
        String errorCode = values.get("errno");
        String errorMessage = values.get("errorMessage");
        String transactionId = values.get("mul_no");
        String paymentUrl = values.get("payurl");

        if (state == null || state.isBlank()) {
            log.error(
                    "[Payment] 페이앱 필수 응답값 누락 - orderNumber: {}, missingField: state",
                    orderNumber
            );

            throw new ProjectException(
                    PaymentErrorCode.INVALID_PG_RESPONSE
            );
        }

        boolean success = SUCCESS_STATE.equals(state);

        if (success
                && (transactionId == null
                || transactionId.isBlank()
                || paymentUrl == null
                || paymentUrl.isBlank())) {
            log.error(
                    "[Payment] 페이앱 성공 응답 필수값 누락 - orderNumber: {}",
                    orderNumber
            );

            throw new ProjectException(
                    PaymentErrorCode.INVALID_PG_RESPONSE
            );
        }

        return new PayAppPaymentResponse(
                success,
                errorCode,
                errorMessage,
                transactionId,
                paymentUrl
        );
    }

    private Map<String, String> parseUrlEncodedResponse(
            String responseBody
    ) {
        Map<String, String> values =
                new LinkedHashMap<>();

        for (String pair : responseBody.split("&")) {
            String[] entry = pair.split("=", 2);

            String key = decode(entry[0]);
            String value = entry.length == 2
                    ? decode(entry[1])
                    : "";

            values.putIfAbsent(key, value);
        }

        return values;
    }

    private String decode(String value) {
        return URLDecoder.decode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private String normalizeErrorCode(String errorCode) {
        if (errorCode == null || errorCode.isBlank()) {
            return "UNKNOWN";
        }

        return errorCode;
    }

    private boolean isTimeout(Throwable throwable) {
        for (
                Throwable cause = throwable;
                cause != null;
                cause = cause.getCause()
        ) {
            if (cause instanceof SocketTimeoutException
                    || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }

        return false;
    }
}
