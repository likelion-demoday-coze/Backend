package demoday.backend.payment.repository;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface PaymentRepository
        extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderNumber(
            String orderNumber
    );

    Optional<Payment> findByOrderNumberAndMemberMemberId(
            String orderNumber,
            Long memberId
    );

    Optional<Payment> findByPaymentKey(
            String paymentKey
    );

    Optional<Payment> findByTid(
            String tid
    );

    boolean existsByPaymentKey(
            String paymentKey
    );

    boolean existsByTid(
            String tid
    );

    boolean existsByMemberMemberIdAndProductProductTypeAndStatusIn(
            Long memberId,
            ProductType productType,
            Collection<PaymentStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Payment p
            JOIN FETCH p.member
            JOIN FETCH p.product
            WHERE p.orderNumber = :orderNumber
            """)
    Optional<Payment> findByOrderNumberForUpdate(
            @Param("orderNumber")
            String orderNumber
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM Payment p
            JOIN FETCH p.member
            JOIN FETCH p.product
            WHERE p.paymentKey = :paymentKey
            """)
    Optional<Payment> findByPaymentKeyForUpdate(
            @Param("paymentKey")
            String paymentKey
    );

    boolean existsByMemberMemberIdAndProductProductTypeAndStatusInAndRequestedAtGreaterThanEqual(
            Long memberId,
            ProductType productType,
            Collection<PaymentStatus> statuses,
            LocalDateTime requestedAt
    );
}
