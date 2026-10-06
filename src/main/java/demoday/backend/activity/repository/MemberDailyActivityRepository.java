package demoday.backend.activity.repository;

import demoday.backend.activity.code.LearningStatus;
import demoday.backend.activity.domain.MemberDailyActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MemberDailyActivityRepository extends JpaRepository<MemberDailyActivity, Long> {

    Optional<MemberDailyActivity> findByMemberMemberIdAndActivityDate(
            Long memberId,
            LocalDate activityDate
    );

    boolean existsByMemberMemberIdAndActivityDateAndLearningStatusIn(
            Long memberId,
            LocalDate activityDate,
            Collection<LearningStatus> learningStatuses
    );

    @Query("""
        SELECT a.activityDate
        FROM MemberDailyActivity a
        WHERE a.member.memberId = :memberId
          AND a.learningStatus IN :statuses
        ORDER BY a.activityDate ASC
        """)
    List<LocalDate> findLearnedDatesByMemberIdAndStatusIn(
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<LearningStatus> statuses
    );
}
