package demoday.backend.payment.repository;

import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.code.ProductType;
import demoday.backend.payment.domain.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
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

    @Query("""
            SELECT p.paymentId
            FROM Payment p
            WHERE p.status = :status
            ORDER BY p.approvedAt ASC, p.paymentId ASC
            """)
    List<Long> findIdsByStatus(
            @Param("status") PaymentStatus status
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

    boolean existsByMemberMemberIdAndProductProductTypeAndStatusInAndRequestedAtGreaterThanEqual(
            Long memberId,
            ProductType productType,
            Collection<PaymentStatus> statuses,
            LocalDateTime requestedAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT p
        FROM Payment p
        JOIN FETCH p.member
        JOIN FETCH p.product
        WHERE p.paymentId = :paymentId
        """)
    Optional<Payment> findByIdForUpdate(
            @Param("paymentId") Long paymentId
    );

    Page<Payment> findAllByMemberMemberIdOrderByRequestedAtDescPaymentIdDesc(
            Long memberId,
            Pageable pageable
    );

    Optional<Payment> findByPaymentIdAndMemberMemberId(
            Long paymentId,
            Long memberId
    );

    @Query("""
        SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
        FROM Payment p
        WHERE p.member.memberId = :memberId
          AND (
              p.status IN :unresolvedStatuses
              OR (
                  p.status = :readyStatus
                  AND p.requestedAt > :validRequestedAt
              )
          )
        """)
    boolean existsBlockingWithdrawalPayment(
            @Param("memberId") Long memberId,
            @Param("unresolvedStatuses")
            Collection<PaymentStatus> unresolvedStatuses,
            @Param("readyStatus") PaymentStatus readyStatus,
            @Param("validRequestedAt") LocalDateTime validRequestedAt
    );
}
