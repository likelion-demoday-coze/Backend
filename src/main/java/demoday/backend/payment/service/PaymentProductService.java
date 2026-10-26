package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.config.ServicePeriodProperties;
import demoday.backend.payment.dto.PaymentProductResponse;
import demoday.backend.payment.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentProductService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ProductRepository productRepository;
    private final Clock clock;
    private final ServicePeriodProperties servicePeriodProperties;

    public List<PaymentProductResponse> getAvailableProducts() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));

        if (!servicePeriodProperties.isSalesOpenAt(now)) {
            return List.of();
        }

        return productRepository.findAvailableProducts(now).stream()
                .map(PaymentProductResponse::from)
                .toList();
    }

    public PaymentProductResponse getAvailableProduct(Long productId) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(KST));

        if (!servicePeriodProperties.isSalesOpenAt(now)) {
            throw new ProjectException(
                    PaymentErrorCode.PRODUCT_NOT_FOUND
            );
        }

        return productRepository.findAvailableProduct(
                        productId,
                        now
                )
                .map(PaymentProductResponse::from)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.PRODUCT_NOT_FOUND
                ));
    }
}
