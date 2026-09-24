package demoday.backend.payment.repository;

import demoday.backend.payment.domain.MemberPass;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPassRepository extends JpaRepository<MemberPass, Long> {
}
