package demoday.backend.quiz.domain;

import demoday.backend.member.domain.Member;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(
        name = "member_question_history",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_member_question_history_member_question",
                columnNames = {"member_id", "question_id"}
        )
)
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberQuestionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_question_history_id")
    private Long memberQuestionHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    public static MemberQuestionHistory create(Member member, QuizQuestion question) {
        return MemberQuestionHistory.builder()
                .member(member)
                .question(question)
                .build();
    }
}
