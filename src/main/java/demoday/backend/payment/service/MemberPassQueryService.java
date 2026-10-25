package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.dto.CurrentMemberPassResponse;
import demoday.backend.payment.dto.MemberPassHistoryPageResponse;
import demoday.backend.payment.repository.MemberPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberPassQueryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MemberRepository memberRepository;
    private final MemberPassRepository memberPassRepository;
    private final Clock clock;

    public CurrentMemberPassResponse getCurrentPass(
            Long memberId
    ) {
        validateActiveMember(memberId);

        LocalDateTime now = LocalDateTime.now(
                clock.withZone(KST)
        );

        return memberPassRepository.findActivePass(
                        memberId,
                        PassStatus.ACTIVE,
                        now
                )
                .map(memberPass ->
                        CurrentMemberPassResponse.active(
                                memberPass,
                                now
                        )
                )
                .orElseGet(
                        CurrentMemberPassResponse::inactive
                );
    }

    public MemberPassHistoryPageResponse getHistories(
            Long memberId,
            Pageable pageable
    ) {
        validateActiveMember(memberId);

        LocalDateTime now = LocalDateTime.now(
                clock.withZone(KST)
        );

        Page<MemberPass> memberPasses = memberPassRepository
                .findAllByMemberMemberIdOrderByStartedAtDescMemberPassIdDesc(
                        memberId,
                        pageable
                );

        return MemberPassHistoryPageResponse.from(
                memberPasses,
                now
        );
    }

    private void validateActiveMember(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        if (!memberRepository.existsByMemberIdAndStatus(
                memberId,
                MemberStatus.ACTIVE
        )) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }
    }
}
