package demoday.backend.activity.repository;

import demoday.backend.activity.domain.MemberDailyActivity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberDailyActivityRepository extends JpaRepository<MemberDailyActivity, Long> {
}
