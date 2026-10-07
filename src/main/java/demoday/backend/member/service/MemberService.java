package demoday.backend.member.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.dto.MemberResponse;
import demoday.backend.member.dto.NicknameAvailabilityResponse;
import demoday.backend.member.dto.NicknameUpdateRequest;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.payment.code.PaymentStatus;
import demoday.backend.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    private static final long PAYMENT_AUTHENTICATION_MINUTES = 30L;

    private static final List<PaymentStatus>
            WITHDRAWAL_BLOCKING_PAYMENT_STATUSES = List.of(
            PaymentStatus.CONFIRMING,
            PaymentStatus.UNKNOWN,
            PaymentStatus.APPROVED
    );

    public NicknameAvailabilityResponse checkNickname(String nickname) {
        return new NicknameAvailabilityResponse(
                !memberRepository.existsByNickname(nickname)
        );
    }

    public Member signup(Long kakaoUserId, String nickname) {
        if (memberRepository.findByKakaoUserId(kakaoUserId).isPresent()
                || memberRepository.existsByNickname(nickname)) {
            throw new ProjectException(GeneralErrorCode.CONFLICT);
        }

        try {
            // 중복 확인 직후 다른 사용자가 같은 닉네임으로 가입하는 경우도 DB 제약으로 방어
            return memberRepository.saveAndFlush(Member.create(kakaoUserId, nickname));
        } catch (DataIntegrityViolationException exception) {
            throw new ProjectException(GeneralErrorCode.CONFLICT);
        }
    }


    public MemberResponse getMyProfile(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));

        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse updateNickname(
            Long memberId,
            NicknameUpdateRequest request
    ) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );

        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(
                    MemberErrorCode.INACTIVE_MEMBER
            );
        }

        String nickname = request.nickname();

        if (member.getNickname().equals(nickname)) {
            return MemberResponse.from(member);
        }

        if (memberRepository.existsByNickname(nickname)) {
            throw new ProjectException(
                    MemberErrorCode.NICKNAME_ALREADY_EXISTS
            );
        }

        member.changeNickname(nickname);

        try {
            memberRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ProjectException(
                    MemberErrorCode.NICKNAME_ALREADY_EXISTS
            );
        }

        return MemberResponse.from(member);
    }

    @Transactional
    public void withdraw(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(
                    GeneralErrorCode.UNAUTHORIZED
            );
        }

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() ->
                        new ProjectException(
                                GeneralErrorCode.NOT_FOUND
                        )
                );

        if (member.isWithdrawn()) {
            throw new ProjectException(
                    MemberErrorCode.ALREADY_WITHDRAWN
            );
        }

        LocalDateTime now = LocalDateTime.now(clock)
                .truncatedTo(ChronoUnit.MICROS);

        boolean blockingPaymentExists =
                paymentRepository.existsBlockingWithdrawalPayment(
                        memberId,
                        WITHDRAWAL_BLOCKING_PAYMENT_STATUSES,
                        PaymentStatus.READY,
                        now.minusMinutes(
                                PAYMENT_AUTHENTICATION_MINUTES
                        )
                );

        if (blockingPaymentExists) {
            throw new ProjectException(
                    MemberErrorCode
                            .WITHDRAWAL_BLOCKED_BY_PENDING_PAYMENT
            );
        }

        member.withdraw(now);
    }
}
