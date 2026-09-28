package demoday.backend.dailyquiz.repository;

import demoday.backend.dailyquiz.code.DailyQuizSessionStatus;
import demoday.backend.dailyquiz.domain.DailyQuizSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;

public interface DailyQuizSessionRepository extends JpaRepository<DailyQuizSession, Long> {

    Optional<DailyQuizSession> findFirstByMemberMemberIdAndStatusInOrderByStartedAtDesc(
            Long memberId,
            Collection<DailyQuizSessionStatus> statuses
    );

    Optional<DailyQuizSession> findByDailyQuizSessionIdAndMemberMemberId(
            Long dailyQuizSessionId,
            Long memberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s
            FROM DailyQuizSession s
            WHERE s.dailyQuizSessionId = :sessionId
              AND s.member.memberId = :memberId
            """)
    Optional<DailyQuizSession> findByIdAndMemberIdForUpdate(
            @Param("sessionId") Long sessionId,
            @Param("memberId") Long memberId
    );
}
