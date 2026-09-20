package demoday.backend.member.service;

import demoday.backend.member.dto.NicknameAvailabilityResponse;
import demoday.backend.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public NicknameAvailabilityResponse checkNickname(String nickname) {
        return new NicknameAvailabilityResponse(
                !memberRepository.existsByNickname(nickname)
        );
    }
}
