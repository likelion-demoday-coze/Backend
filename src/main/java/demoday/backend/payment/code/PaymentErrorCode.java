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

    ACTIVE_PASS_EXISTS(
            HttpStatus.CONFLICT,
            "PAYMENT_409_2",
            "현재 이용 중인 패스가 있습니다."
    ),

    PAYMENT_ALREADY_PROCESSED(
            HttpStatus.CONFLICT,
            "PAYMENT_409_3",
            "이미 처리된 결제 주문입니다."
    ),

    PAYMENT_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "PAYMENT_409_5",
            "이미 진행 중인 결제가 있습니다."
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

    AMOUNT_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_7",
            "주문 금액과 승인 금액이 일치하지 않습니다."
    ),

    PG_COMMUNICATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "PAYMENT_502_2",
            "결제사와 통신하는 중 오류가 발생했습니다."
    ),

    FULFILLMENT_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "PAYMENT_500_1",
            "결제는 승인되었지만 상품 지급에 실패했습니다."
    ),
    PAYAPP_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY,
            "PAYMENT_502_3",
            "결제 요청이 거절되었습니다."
    ),

    INVALID_PG_RESPONSE(
            HttpStatus.BAD_GATEWAY,
            "PAYMENT_502_4",
            "결제사의 응답을 처리할 수 없습니다."
    ),

    INVALID_FEEDBACK(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_8",
            "유효하지 않은 결제 통보입니다."
    ),

    INVALID_FEEDBACK_AUTHENTICATION(
            HttpStatus.UNAUTHORIZED,
            "PAYMENT_401_1",
            "결제 통보 인증에 실패했습니다."
    ),

    PAYMENT_INFORMATION_MISMATCH(
            HttpStatus.BAD_REQUEST,
            "PAYMENT_400_9",
            "주문 정보와 결제 통보가 일치하지 않습니다."
    ),

    PG_TIMEOUT(
            HttpStatus.GATEWAY_TIMEOUT,
            "PAYMENT_504_1",
            "결제사 응답 시간이 초과되었습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
