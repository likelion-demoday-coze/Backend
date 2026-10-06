package demoday.backend.payment.service;

import demoday.backend.global.exception.ProjectException;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.dto.PaymentProductResponse;
import demoday.backend.payment.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentProductService {

    private final ProductRepository productRepository;
    private final Clock clock;

    public List<PaymentProductResponse> getAvailableProducts() {
        LocalDateTime now = LocalDateTime.now(clock);

        return productRepository.findAvailableProducts(now).stream()
                .map(PaymentProductResponse::from)
                .toList();
    }

    public PaymentProductResponse getAvailableProduct(Long productId) {
        return productRepository.findAvailableProduct(
                        productId,
                        LocalDateTime.now(clock)
                )
                .map(PaymentProductResponse::from)
                .orElseThrow(() -> new ProjectException(
                        PaymentErrorCode.PRODUCT_NOT_FOUND
                ));
    }
}
