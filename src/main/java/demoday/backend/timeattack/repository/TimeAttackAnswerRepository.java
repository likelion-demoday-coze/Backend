package demoday.backend.timeattack.repository;

import demoday.backend.timeattack.domain.TimeAttackAnswer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
    findAllByTimeAttackSessionTimeAttackSessionIdOrderByAnsweredAtAscTimeAttackAnswerIdAsc (
            Long timeAttackSessionId
    );
}
