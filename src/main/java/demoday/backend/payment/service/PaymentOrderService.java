package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.config.KorpayProperties;
import demoday.backend.payment.domain.Payment;
import demoday.backend.payment.domain.Product;
import demoday.backend.payment.dto.PaymentOrderResponse;
import demoday.backend.payment.repository.MemberPassRepository;
import demoday.backend.payment.repository.PaymentRepository;
import demoday.backend.payment.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentOrderService {

    private static final DateTimeFormatter EDI_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final DateTimeFormatter ORDER_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final long PAYMENT_AUTHENTICATION_MINUTES = 30L;

    private static final List<PaymentStatus> PURCHASER_STATUSES =
            List.of(
                    PaymentStatus.APPROVED,
                    PaymentStatus.COMPLETED
            );

    private static final List<PaymentStatus> UNKNOWN_STATUSES =
            List.of(PaymentStatus.UNKNOWN);

    private static final List<PaymentStatus> READY_STATUSES =
            List.of(PaymentStatus.READY);

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final MemberPassRepository memberPassRepository;
    private final KorpayProperties korpayProperties;
    private final Clock clock;

    @Transactional
    public PaymentOrderResponse createOrder(
            Long memberId,
            String productCode
    ) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );

        Product product = productRepository.findByProductCode(productCode)
                .orElseThrow(() ->
                        new ProjectException(
                                PaymentErrorCode.PRODUCT_NOT_FOUND
                        )
                );

        LocalDateTime now = LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MICROS);

        validateProduct(product, now);

        if (product.getProductType() == ProductType.PASS) {
            validatePassPurchase(memberId, now);
        }

        String orderNumber = createOrderNumber(now);
        String ediDate = now.format(EDI_DATE_FORMAT);

        Payment payment = paymentRepository.save(
                Payment.create(
                        member,
                        product,
                        orderNumber,
                        product.getPrice(),
                        ediDate,
                        now
                )
        );

        String hashKey = generateHash(
                korpayProperties.merchantId(),
                ediDate,
                payment.getAmount(),
                korpayProperties.merchantKey()
        );

        log.info(
                "[Payment] 주문 생성 완료 - paymentId: {}, orderNumber: {}, memberId: {}, productCode: {}, amount: {}",
                payment.getPaymentId(),
                payment.getOrderNumber(),
                memberId,
                product.getProductCode(),
                payment.getAmount()
        );

        return PaymentOrderResponse.of(
                payment,
                korpayProperties.merchantId(),
                korpayProperties.payMethod(),
                korpayProperties.returnUrl(),
                hashKey
        );
    }

    private void validateProduct(Product product, LocalDateTime now) {
        if (!product.isAvailableAt(now)) {
            throw new ProjectException(
                    PaymentErrorCode.PRODUCT_NOT_AVAILABLE
            );
        }
    }

    private void validatePassPurchase(Long memberId, LocalDateTime now) {
        // 상품 지급까지 끝난 구매 이력
        if (memberPassRepository.existsByMemberMemberId(memberId)) {
            throw new ProjectException(
                    PaymentErrorCode.PASS_ALREADY_PURCHASED
            );
        }

        // 승인됐지만 상품 지급이 완료되지 않은 결제
        boolean purchasedPaymentExists =
                paymentRepository.existsByMemberMemberIdAndProductProductTypeAndStatusIn(
                        memberId,
                        ProductType.PASS,
                        PURCHASER_STATUSES
                );

        if (purchasedPaymentExists) {
            throw new ProjectException(
                    PaymentErrorCode.PASS_ALREADY_PURCHASED
            );
        }

        // 승인 결과 확인이 필요한 결제 있을 시 새 결제 열지 않기
        boolean unknownPaymentExists =
                paymentRepository.existsByMemberMemberIdAndProductProductTypeAndStatusIn(
                        memberId,
                        ProductType.PASS,
                        UNKNOWN_STATUSES
                );

        if (unknownPaymentExists) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
            );
        }

        // 아직 유효한 결제창 있으면 중복 주문 생성 막기
        LocalDateTime validRequestedAt =
                now.minusMinutes(PAYMENT_AUTHENTICATION_MINUTES);

        boolean readyPaymentExists = paymentRepository.existsByMemberMemberIdAndProductProductTypeAndStatusInAndRequestedAtGreaterThanEqual(
                memberId,
                ProductType.PASS,
                READY_STATUSES,
                validRequestedAt
        );

        if (readyPaymentExists) {
            throw new ProjectException(
                    PaymentErrorCode.PAYMENT_ALREADY_PROCESSED
            );
        }
    }

    private String createOrderNumber(LocalDateTime now) {
        String randomValue = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase();

        return "PAY"
                + now.format(ORDER_DATE_FORMAT)
                + randomValue;
    }

    private String generateHash(
            String merchantId,
            String ediDate,
            Integer amount,
            String merchantKey
    ) {
        String source = merchantId
                + ediDate
                + amount
                + merchantKey;

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    source.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new ProjectException(
                    PaymentErrorCode.HASH_GENERATION_FAILED
            );
        }
    }
}
