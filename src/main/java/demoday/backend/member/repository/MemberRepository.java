package demoday.backend.member.repository;

import demoday.backend.member.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByKakaoUserId(Long kakaoUserId);

    boolean existsByNickname(String nickname);
}
