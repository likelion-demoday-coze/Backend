package demoday.backend.streak.repository;

import demoday.backend.streak.domain.StreakRecoveryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import demoday.backend.streak.code.StreakRecoveryStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface StreakRecoveryEventRepository extends JpaRepository<StreakRecoveryEvent, Long> {
    Optional<StreakRecoveryEvent> findByStreakRecoveryEventIdAndMemberMemberId(Long eventId, Long memberId);
    Optional<StreakRecoveryEvent> findFirstByMemberMemberIdOrderByMissedDateDesc(Long memberId);
    Optional<StreakRecoveryEvent> findFirstByMemberMemberIdAndStatusOrderByMissedDateDesc(Long memberId, StreakRecoveryStatus status);
    boolean existsByMemberMemberIdAndRequestedAtGreaterThanEqualAndRequestedAtLessThan(Long memberId, LocalDateTime from, LocalDateTime to);

    @Query("SELECT DISTINCT e.member.memberId FROM StreakRecoveryEvent e WHERE e.status = :status AND e.requestedAt < :before")
    List<Long> findOverdueMemberIds(@Param("status") StreakRecoveryStatus status, @Param("before") LocalDateTime before);
}
