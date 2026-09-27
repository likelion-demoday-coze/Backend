package demoday.backend.activity.repository;

import demoday.backend.activity.domain.MemberDailyActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface MemberDailyActivityRepository extends JpaRepository<MemberDailyActivity, Long> {

    Optional<MemberDailyActivity> findByMemberMemberIdAndActivityDate(
            Long memberId,
            LocalDate activityDate
    );
}
