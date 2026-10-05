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
import java.time.LocalDate;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 후보 조회에는 잠금을 잡지 않고, 처리 시 회원 한 명씩 잠근 뒤 조건을 다시 검사한다.
    @Query("""
            SELECT m.memberId FROM Member m
            WHERE m.status = :status AND m.currentStreak > 0
              AND (m.lastLearningDate IS NULL OR m.lastLearningDate < :yesterday)
            ORDER BY m.memberId
            """)
    List<Long> findStreakPenaltyCandidateIds(
            @Param("status") MemberStatus status, @Param("yesterday") LocalDate yesterday
    );

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

    // 개수만 비교하지 않고 실제 대상 회원의 스냅샷 누락 여부를 잠금 없이 확인한다.
    @Query("""
            SELECT COUNT(m) FROM Member m
            WHERE m.status = :status
              AND (m.createdAt IS NULL OR m.createdAt < :closedAt)
              AND NOT EXISTS (
                  SELECT s FROM StockDailySnapshot s
                  WHERE s.member = m AND s.snapshotDate = :snapshotDate
              )
            """)
    long countMissingStockSnapshots(
            @Param("status") MemberStatus status,
            @Param("closedAt") LocalDateTime closedAt,
            @Param("snapshotDate") LocalDate snapshotDate
    );

    @Query("""
            SELECT m.memberId FROM Member m
            WHERE m.status = :status
              AND (m.createdAt IS NULL OR m.createdAt < :closedAt)
              AND NOT EXISTS (
                  SELECT s FROM StockDailySnapshot s
                  WHERE s.member = m AND s.snapshotDate = :snapshotDate
              )
            ORDER BY m.memberId
            """)
    List<Long> findMissingStockSnapshotMemberIds(
            @Param("status") MemberStatus status,
            @Param("closedAt") LocalDateTime closedAt,
            @Param("snapshotDate") LocalDate snapshotDate
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT m FROM Member m
            WHERE m.memberId IN :memberIds AND m.status = :status
              AND (m.createdAt IS NULL OR m.createdAt < :closedAt)
            ORDER BY m.memberId
            """)
    List<Member> findSnapshotCandidatesForUpdate(
            @Param("status") MemberStatus status,
            @Param("closedAt") LocalDateTime closedAt,
            @Param("memberIds") List<Long> memberIds
    );
}
