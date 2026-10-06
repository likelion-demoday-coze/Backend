package demoday.backend.dailyquiz.repository;

import demoday.backend.dailyquiz.code.DailyQuizAttemptType;
import demoday.backend.dailyquiz.domain.DailyQuizAttempt;
import demoday.backend.member.repository.QuizStatisticsProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DailyQuizAttemptRepository extends JpaRepository<DailyQuizAttempt, Long> {

    @EntityGraph(attributePaths = "selectedOption")
    Optional<DailyQuizAttempt> findBySessionQuestionSessionQuestionIdAndAttemptType(
            Long sessionQuestionId,
            DailyQuizAttemptType attemptType
    );

    @EntityGraph(attributePaths = {"sessionQuestion", "sessionQuestion.question", "selectedOption"})
    List<DailyQuizAttempt> findAllBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeOrderBySessionQuestionQuestionQuestionIdAsc(
            Long dailyQuizSessionId,
            DailyQuizAttemptType attemptType
    );

    long countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptType(
            Long dailyQuizSessionId,
            DailyQuizAttemptType attemptType
    );

    long countBySessionQuestionDailyQuizSessionDailyQuizSessionIdAndAttemptTypeAndCorrectFalse(
            Long dailyQuizSessionId,
            DailyQuizAttemptType attemptType
    );

    @Query("""
        SELECT
            COUNT(a) AS totalCount,
            COALESCE(
                SUM(
                    CASE
                        WHEN a.correct = true THEN 1
                        ELSE 0
                    END
                ),
                0
            ) AS correctCount
        FROM DailyQuizAttempt a
        WHERE a.sessionQuestion.dailyQuizSession.member.memberId = :memberId
          AND a.attemptType = :attemptType
        """)
    QuizStatisticsProjection findStatisticsByMemberIdAndAttemptType(
            @Param("memberId") Long memberId,
            @Param("attemptType") DailyQuizAttemptType attemptType
    );
}
