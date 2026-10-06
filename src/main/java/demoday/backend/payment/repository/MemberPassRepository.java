package demoday.backend.payment.repository;

import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.domain.MemberPass;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberPassRepository extends JpaRepository<MemberPass, Long> {

    @Query("""
            SELECT p
            FROM MemberPass p
            WHERE p.member.memberId = :memberId
              AND p.status = :status
              AND p.startedAt <= :now
              AND p.expiresAt > :now
            ORDER BY p.expiresAt DESC
            LIMIT 1
            """)
    Optional<MemberPass> findActivePass(
            @Param("memberId") Long memberId,
            @Param("status") PassStatus status,
            @Param("now") LocalDateTime now
    );

    boolean existsByMemberMemberId(Long memberId);
}
