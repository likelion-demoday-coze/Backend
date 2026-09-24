package demoday.backend.quiz.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(
        name = "quiz_option",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_quiz_option_question_number",
                columnNames = {"question_id", "option_number"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class QuizOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "option_id")
    private Long optionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @Column(name = "option_number", nullable = false)
    private Integer optionNumber;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(nullable = false)
    private Boolean correct;

    public static QuizOption create(
            QuizQuestion question,
            Integer optionNumber,
            String content,
            Boolean correct
    ) {
        return QuizOption.builder()
                .question(question)
                .optionNumber(optionNumber)
                .content(content)
                .correct(correct)
                .build();
    }
}
