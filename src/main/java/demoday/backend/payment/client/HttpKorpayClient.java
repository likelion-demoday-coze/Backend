package demoday.backend.payment.client;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.client.dto.KorpayConfirmResponse;
import demoday.backend.payment.code.PaymentErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;

@Slf4j
@Component
public class HttpKorpayClient implements KorpayClient {

    private final RestClient restClient;

    public HttpKorpayClient(
            @Qualifier("korpayRestClient")
            RestClient restClient
    ) {
        this.restClient = restClient;
    }

    @Override
    public KorpayConfirmResponse confirm(String paymentKey) {
        if (paymentKey == null || paymentKey.isBlank()) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_KEY_MISSING
            );
        }

        try {
            // 코페이 문서에 따라 paymentKey 쿼리 파라미터로 전달하고 POST /payments/confirm으로 최종 승인 요청
            KorpayConfirmResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/payments/confirm")
                            .queryParam(
                                    "paymentKey",
                                    paymentKey
                            )
                            .build()
                    )
                    .retrieve()
                    .body(KorpayConfirmResponse.class);

            // HTTP 요청은 성공했지만 응답 본문이 없으면 실제 승인 여부를 확정할 수 없음
            if (response == null) {
                throw new ProjectException(
                        PaymentErrorCode.CONFIRM_RESULT_UNKNOWN
                );
            }

            return response;
        } catch (ResourceAccessException exception) {
            // 연결 또는 응답 시간 초과가 발생하면 실제 PG 승인 여부가 불분명할 수 있으므로 일반 실패로 확정하지 않음
            if (isTimeout(exception)) {
                log.error(
                        "[Payment] 코페이 승인 요청 시간 초과",
                        exception
                );

                throw new ProjectException(
                        PaymentErrorCode.CONFIRM_TIMEOUT
                );
            }

            log.error(
                    "[Payment] 코페이 승인 API 통신 실패",
                    exception
            );

            throw new ProjectException(
                    PaymentErrorCode.PG_COMMUNICATION_FAILED
            );
        } catch (RestClientResponseException exception) {
            // PG 서버가 4xx 또는 5xx를 반환한 경우
            log.error(
                    "[Payment] 코페이 승인 API 오류 응답 - status: {}",
                    exception.getStatusCode(),
                    exception
            );

            throw new ProjectException(
                    PaymentErrorCode.PG_COMMUNICATION_FAILED
            );
        }
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
