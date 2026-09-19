package demoday.backend.member.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Entity
@Table(name = "members")
@Builder(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "kakao_user_id", nullable = false, unique = true)
    private Long kakaoUserId;

    @Column(nullable = false, unique = true, length = 10)
    private String nickname;

    public static Member create(Long kakaoUserId, String nickname) {
        return Member.builder()
                .kakaoUserId(kakaoUserId)
                .nickname(nickname)
                .build();
    }
}
