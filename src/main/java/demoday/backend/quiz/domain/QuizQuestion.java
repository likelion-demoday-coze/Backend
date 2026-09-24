package demoday.backend.quiz.domain;

import demoday.backend.quiz.code.QuizCategory;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(name = "quiz_question")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private QuizCategory category;

    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @Column(nullable = false)
    private Boolean active;

    public static QuizQuestion create(
            QuizCategory category,
            String questionType,
            String content,
            String explanation,
            Boolean active
    ) {
        return QuizQuestion.builder()
                .category(category)
                .questionType(questionType)
                .content(content)
                .explanation(explanation)
                .active(active)
                .build();
    }
}
