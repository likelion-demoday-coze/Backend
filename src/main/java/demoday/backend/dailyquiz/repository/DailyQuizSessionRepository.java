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
import java.util.List;
import java.time.LocalDateTime;

public interface DailyQuizSessionRepository extends JpaRepository<DailyQuizSession, Long> {
    @Query("SELECT s.startedAt FROM DailyQuizSession s WHERE s.member.memberId = :memberId AND s.endStock IS NOT NULL AND s.startedAt < :before")
    List<LocalDateTime> findOriginalCompletedStartTimes(@Param("memberId") Long memberId, @Param("before") LocalDateTime before);
    // endStock은 원본 5문제를 모두 제출한 때에만 기록된다. 오답 재풀이 만료 후에도 남는다.
    Optional<DailyQuizSession> findFirstByMemberMemberIdAndEndStockIsNotNullOrderByStartedAtDesc(Long memberId);

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
