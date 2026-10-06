package demoday.backend.payment.repository;

import demoday.backend.payment.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findByProductCode(String productCode);

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.active = true
              AND (p.saleStartedAt IS NULL OR p.saleStartedAt <= :now)
              AND (p.saleEndedAt IS NULL OR p.saleEndedAt > :now)
            ORDER BY p.productId ASC
            """)
    List<Product> findAvailableProducts(
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT p
            FROM Product p
            WHERE p.productId = :productId
              AND p.active = true
              AND (p.saleStartedAt IS NULL OR p.saleStartedAt <= :now)
              AND (p.saleEndedAt IS NULL OR p.saleEndedAt > :now)
            """)
    Optional<Product> findAvailableProduct(
            @Param("productId") Long productId,
            @Param("now") LocalDateTime now
    );
}
