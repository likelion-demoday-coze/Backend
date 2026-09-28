package demoday.backend.activity.repository;

import demoday.backend.activity.code.LearningStatus;
import demoday.backend.activity.domain.MemberDailyActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
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
}
