package demoday.backend.timeattack.repository;

import demoday.backend.timeattack.domain.TimeAttackAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TimeAttackAnswerRepository extends JpaRepository<TimeAttackAnswer, Long> {

    boolean existsByTimeAttackSessionTimeAttackSessionIdAndQuestionQuestionId(
            Long timeAttackSessionId,
            Long questionId
    );

    @EntityGraph(attributePaths = {
            "question",
            "selectedOption"
    })
    List<TimeAttackAnswer>
    findAllByTimeAttackSessionTimeAttackSessionIdOrderByAnsweredAtAscTimeAttackAnswerIdAsc(
            Long timeAttackSessionId
    );

    @Query("""
        SELECT a.correct
        FROM TimeAttackAnswer a
        WHERE a.timeAttackSession.timeAttackSessionId = :sessionId
        ORDER BY a.answeredAt DESC, a.timeAttackAnswerId DESC
        """)
    List<Boolean> findCorrectResultsBySessionIdOrderByLatest(
            @Param("sessionId") Long sessionId
    );
}
