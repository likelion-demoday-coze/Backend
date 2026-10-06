package demoday.backend.payment.controller;

import demoday.backend.global.api.ApiResponse;
import demoday.backend.global.api.code.GeneralSuccessCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.auth.service.FrontendRedirectService;
import demoday.backend.payment.dto.*;
import demoday.backend.payment.service.PaymentCallbackService;
import demoday.backend.payment.service.PaymentOrderService;
import demoday.backend.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Tag(name = "Payment", description = "코페이 인증결제 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentOrderService paymentOrderService;
    private final PaymentCallbackService paymentCallbackService;
    private final PaymentQueryService paymentQueryService;
    private final FrontendRedirectService frontendRedirectService;

    @Value("${app.payment.frontend-success-path:/payment/success}")
    private String frontendSuccessPath;

    @Value("${app.payment.frontend-failure-path:/payment/failure}")
    private String frontendFailurePath;

    @Operation(
            summary = "결제 주문 생성",
            description = """
                    로그인 회원의 결제 주문을 생성합니다.
                    클라이언트는 상품 코드만 전달하며 가격은 서버의 상품 정보를 사용합니다.
                    POST 요청이므로 세션 인증과 CSRF 토큰이 필요합니다.
                    """
    )
    @PostMapping("/orders")
    public ResponseEntity<ApiResponse<PaymentOrderResponse>> createOrder(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Valid @RequestBody PaymentOrderRequest request
    ) {
        PaymentOrderResponse response = paymentOrderService.createOrder(
                memberId,
                request.productCode(),
                request.redirectUrl()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(
                        GeneralSuccessCode.CREATED,
                        response
                ));
    }

    @Operation(
            summary = "코페이 결제 인증 콜백",
            description = """
                    코페이가 인증 결과를 form-urlencoded 형식으로 전달하는 콜백입니다.
                    외부 PG 호출 경로이므로 로그인과 CSRF 토큰은 요구하지 않습니다.
                    콜백 검증, 최종 승인 및 상품 지급 후 프론트 결과 화면으로 이동합니다.
                    """
    )
    @PostMapping(
            value = "/callback",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public ResponseEntity<Void> callback(
            @ModelAttribute KorpayCallbackRequest request
    ) {
        try {
            PaymentConfirmResultResponse result =
                    paymentCallbackService.confirm(request);

            URI location = UriComponentsBuilder
                    .fromUriString(resolveRedirectUrl(request.reserved()))
                    .path(frontendSuccessPath)
                    .queryParam("orderNumber", result.orderNumber())
                    .build()
                    .encode()
                    .toUri();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(location)
                    .build();
        } catch (ProjectException exception) {
            URI location = UriComponentsBuilder
                    .fromUriString(resolveRedirectUrl(request.reserved()))
                    .path(frontendFailurePath)
                    .queryParam("orderNumber", request.orderNumber())
                    .queryParam("code", exception.getErrorCode().getCode())
                    .build()
                    .encode()
                    .toUri();

            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(location)
                    .build();
        }
    }

    private String resolveRedirectUrl(String requestedUrl) {
        try {
            return frontendRedirectService.validate(requestedUrl);
        } catch (ProjectException ignored) {
            return frontendRedirectService.validate(null);
        }
    }

    @Operation(
            summary = "내 결제 상태 조회",
            description = """
                로그인 회원이 주문번호로 자신의 결제 상태를 조회합니다.
                READY는 인증 대기, UNKNOWN은 승인 여부 확인 필요,
                APPROVED는 승인 완료 후 상품 지급 대기,
                COMPLETED는 상품 지급까지 완료된 상태입니다.
                다른 회원의 주문은 조회할 수 없습니다.
                """
    )
    @GetMapping("/{orderNumber}")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(
            @Parameter(hidden = true)
            @AuthenticationPrincipal Long memberId,

            @Parameter(
                    description = "결제 주문번호",
                    example = "PAY20261006123000A1B2C3D4E5F6"
            )
            @PathVariable String orderNumber
    ) {
        return ApiResponse.onSuccess(
                GeneralSuccessCode.OK,
                paymentQueryService.getStatus(
                        memberId,
                        orderNumber
                )
        );
    }
}
