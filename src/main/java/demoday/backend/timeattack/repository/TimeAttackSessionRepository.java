package demoday.backend.timeattack.repository;

import demoday.backend.timeattack.domain.TimeAttackSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TimeAttackSessionRepository extends JpaRepository<TimeAttackSession, Long> {

    Optional<TimeAttackSession>
    findByTimeAttackSessionIdAndMemberMemberId(
            Long timeAttackSessionId,
            Long memberId
    );

    // 답안 제출 및 정상 처리 완료용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM TimeAttackSession s 
            WHERE s.timeAttackSessionId = :sessionId
                AND s.member.memberId = :memberId
            """)
    Optional<TimeAttackSession> findByIdAndMemberIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId
    );
}
