package demoday.backend.quiz.repository;

import demoday.backend.quiz.code.QuizCategory;
import demoday.backend.quiz.domain.QuizQuestion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    @Query("""
            SELECT q
            FROM QuizQuestion q
            WHERE q.category = :category
              AND q.active = true
              AND NOT EXISTS (
                  SELECT h.memberQuestionHistoryId
                  FROM MemberQuestionHistory h
                  WHERE h.member.memberId = :memberId
                    AND h.question = q
              )
            ORDER BY q.questionId ASC
            """)
    List<QuizQuestion> findAvailableQuestions(
            @Param("memberId") Long memberId,
            @Param("category") QuizCategory category,
            Pageable pageable
    );

    @Query("""
        SELECT q
        FROM QuizQuestion q
        WHERE q.category = :category
          AND q.active = true
        ORDER BY q.questionId ASC
        """)
    List<QuizQuestion> findActiveQuestionsByCategory(
            @Param("category") QuizCategory category,
            Pageable pageable
    );

    @Query("""
        SELECT q
        FROM QuizQuestion q
        WHERE q.questionId = :questionId
          AND q.category = :category
          AND q.active = true
        """)
    Optional<QuizQuestion> findActiveQuestion(
            @Param("questionId") Long questionId,
            @Param("category") QuizCategory category
    );
}
