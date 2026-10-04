package demoday.backend.member.repository;

import demoday.backend.member.domain.Member;
import demoday.backend.member.code.MemberStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoUserId(Long kakaoUserId);

    boolean existsByNickname(String nickname);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Member m WHERE m.memberId = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m
            FROM Member m
            WHERE m.status = :status
              AND (m.createdAt IS NULL OR m.createdAt < :closedAt)
            ORDER BY m.memberId
            """)
    List<Member> findAllByStatusAndCreatedBeforeForUpdate(
            @Param("status") MemberStatus status,
            @Param("closedAt") LocalDateTime closedAt
    );
}
