package demoday.backend.payment.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PassStatus;
import demoday.backend.payment.code.PaymentErrorCode;
import demoday.backend.payment.domain.MemberPass;
import demoday.backend.payment.dto.CurrentMemberPassResponse;
import demoday.backend.payment.dto.MemberPassHistoryPageResponse;
import demoday.backend.payment.repository.MemberPassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
            int page,
            int size
    ) {
        validateActiveMember(memberId);
        validatePageRequest(page, size);

        LocalDateTime now = LocalDateTime.now(
                clock.withZone(KST)
        );

        Page<MemberPass> memberPasses = memberPassRepository
                .findAllByMemberMemberIdOrderByStartedAtDescMemberPassIdDesc(
                        memberId,
                        PageRequest.of(page, size)
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

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ProjectException(
                    PaymentErrorCode.INVALID_PAGE_REQUEST
            );
        }
    }
}
