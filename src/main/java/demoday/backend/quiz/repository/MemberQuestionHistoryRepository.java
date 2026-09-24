package demoday.backend.quiz.repository;

import demoday.backend.quiz.domain.MemberQuestionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberQuestionHistoryRepository extends JpaRepository<MemberQuestionHistory, Long> {
}
