package demoday.backend.payment.code;

import demoday.backend.global.api.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements BaseErrorCode {

    PRODUCT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PAYMENT_404_1",
            "결제 상품을 찾을 수 없습니다."
    ),

    ORDER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "PAYMENT_404_2",
            "결제 주문을 찾을 수 없습니다."
    ),

    PRODUCT_NOT_AVAILABLE(
            HttpStatus.CONFLICT,
            "PAYMENT_409_1",
            "현재 구매할 수 없는 상품입니다."
    ),

    PASS_ALREADY_PURCHASED(
            HttpStatus.CONFLICT,
            "PAYMENT_409_2",
            "일주일 패스는 회원당 한 번만 구매할 수 있습니다."
    ),

    PAYMENT_ALREADY_PROCESSED(
            HttpStatus.CONFLICT,
            "PAYMENT_409_3",
            "이미 처리된 결제 주문입니다."
    ),

    DUPLICATE_TRANSACTION(
            HttpStatus.CONFLICT,
            "PAYMENT_409_4",
            "이미 처리된 결제 거래입니다."
    ),

    INVALID_ORDER_NUMBER(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_1",
            "유효하지 않은 주문번호입니다."
    ),

    INVALID_CALLBACK(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_3",
            "유효하지 않은 결제 인증 결과입니다."
    ),

    PAYMENT_KEY_MISSING(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_4",
            "결제 승인에 필요한 결제 키가 없습니다."
    ),

    MERCHANT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_5",
            "가맹점 정보가 일치하지 않습니다."
    ),

    ORDER_NUMBER_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_6",
            "주문번호가 일치하지 않습니다."
    ),

    AMOUNT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_7",
            "주문 금액과 승인 금액이 일치하지 않습니다."
    ),

    PRODUCT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_8",
            "주문 상품과 승인 상품이 일치하지 않습니다."
    ),

    AUTHENTICATION_FAILED(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_9",
            "결제 인증에 실패했습니다."
    ),

    PAYMENT_SESSION_EXPIRED(
            HttpStatus.GONE,
            "PAYMENT_410_1",
            "결제 인증 세션이 만료되었습니다."
    ),

    CONFIRM_FAILED(
            HttpStatus.BAD_GATEWAY,
            "PAYMENT_502_1",
            "결제 승인에 실패했습니다."
    ),

    PG_COMMUNICATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "PAYMENT_502_2",
            "결제사와 통신하는 중 오류가 발생했습니다."
    ),

    CONFIRM_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT,
            "PAYMENT_504_1",
            "결제 승인 요청 시간이 초과되었습니다."
    ),

    CONFIRM_RESULT_UNKNOWN(
            HttpStatus.SERVICE_UNAVAILABLE,
            "PAYMENT_503_1",
            "결제 승인 여부를 확인할 수 없습니다."
    ),

    FULFILLMENT_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PAYMENT_500_1",
            "결제는 승인되었지만 상품 지급에 실패했습니다."
    ),

    HASH_GENERATION_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PAYMENT_500_2",
            "결제 인증값 생성에 실패했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
