package demoday.backend.fish.service;

import demoday.backend.fish.code.FishErrorCode;
import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.domain.FishTransaction;
import demoday.backend.fish.dto.FishBalanceResponse;
import demoday.backend.fish.dto.FishTransactionPageResponse;
import demoday.backend.fish.dto.FishTransactionResponse;
import demoday.backend.fish.repository.FishTransactionRepository;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FishService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9:_-]{1,100}");
    private static final Sort HISTORY_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("fishTransactionId")
    );

    private final MemberRepository memberRepository;
    private final FishTransactionRepository fishTransactionRepository;
    private final Clock clock;

    public FishBalanceResponse getBalance(Long memberId) {
        Member member = findActiveMember(memberId, false);
        return new FishBalanceResponse(member.getFishBalance());
    }

    public FishTransactionPageResponse getTransactions(Long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ProjectException(FishErrorCode.INVALID_PAGE);
        }
        findActiveMember(memberId, false);
        return FishTransactionPageResponse.from(
                fishTransactionRepository.findAllByMemberMemberId(
                        memberId, PageRequest.of(page, size, HISTORY_SORT)
                )
        );
    }

    /** 다른 서비스에서 호출한다. amount는 양수이며, 기존 트랜잭션이 있으면 함께 참여한다. */
    @Transactional
    public FishTransactionResponse credit(
            Long memberId, long amount, FishTransactionType type,
            Long referenceId, String idempotencyKey
    ) {
        validateCommand(amount, type, referenceId, idempotencyKey, true);
        return changeBalance(memberId, amount, type, referenceId, idempotencyKey);
    }

    /** amount는 양수로 전달하고 거래 이력에는 음수로 저장한다. */
    @Transactional
    public FishTransactionResponse debit(
            Long memberId, long amount, FishTransactionType type,
            Long referenceId, String idempotencyKey
    ) {
        validateCommand(amount, type, referenceId, idempotencyKey, false);
        return changeBalance(memberId, -amount, type, referenceId, idempotencyKey);
    }

    private FishTransactionResponse changeBalance(
            Long memberId, long signedAmount, FishTransactionType type,
            Long referenceId, String idempotencyKey
    ) {
        // 모든 변경은 회원 잠금 -> 거래 확인 순서를 따른다.
        Member member = findActiveMember(memberId, true);
        FishTransaction existing = fishTransactionRepository.findByIdempotencyKey(idempotencyKey)
                .orElse(null);

        if (existing != null) {
            if (!Objects.equals(existing.getMember().getMemberId(), memberId)
                    || !existing.getIdempotencyKey().equals(idempotencyKey)
                    || existing.getAmount() != signedAmount
                    || existing.getTransactionType() != type
                    || !Objects.equals(existing.getReferenceId(), referenceId)) {
                throw new ProjectException(FishErrorCode.IDEMPOTENCY_CONFLICT);
            }
            return FishTransactionResponse.from(existing);
        }

        if (signedAmount > 0) {
            member.addFish(signedAmount);
        } else {
            member.deductFish(-signedAmount);
        }

        // 조회한 Member는 영속 상태이므로 트랜잭션 커밋 시 변경 감지로 잔액이 저장된다.
        FishTransaction transaction = FishTransaction.create(
                member, type, signedAmount, member.getFishBalance(), referenceId,
                idempotencyKey, LocalDateTime.now(clock.withZone(KST))
        );
        try {
            // IDENTITY 전략은 save 시 INSERT를 실행한다. 전체 영속성 컨텍스트를 flush하지 않는다.
            return FishTransactionResponse.from(fishTransactionRepository.save(transaction));
        } catch (DataIntegrityViolationException exception) {
            if (isFishUniqueViolation(exception)) {
                // 실패한 트랜잭션에서는 다시 조회/재시도하지 않고 호출자까지 롤백시킨다.
                throw new ProjectException(FishErrorCode.IDEMPOTENCY_CONFLICT);
            }
            throw exception;
        }
    }

    private boolean isFishUniqueViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getKind() == ConstraintViolationException.ConstraintKind.UNIQUE
                    && violation.getSQL() != null
                    && violation.getSQL().toLowerCase(Locale.ROOT).contains("insert into fish_transaction")) {
                return true;
            }
        }
        return false;
    }

    private Member findActiveMember(Long memberId, boolean forUpdate) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }
        Member member = (forUpdate
                ? memberRepository.findByIdForUpdate(memberId)
                : memberRepository.findById(memberId))
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(FishErrorCode.INACTIVE_MEMBER);
        }
        return member;
    }

    private void validateCommand(
            long amount, FishTransactionType type, Long referenceId,
            String idempotencyKey, boolean credit
    ) {
        if (amount <= 0) {
            throw new ProjectException(MemberErrorCode.INVALID_FISH_AMOUNT);
        }
        if (type == null || (credit ? !type.allowsCredit() : !type.allowsDebit())) {
            throw new ProjectException(FishErrorCode.INVALID_TRANSACTION_TYPE);
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ProjectException(FishErrorCode.INVALID_IDEMPOTENCY_KEY);
        }
        if (referenceId != null && referenceId <= 0) {
            throw new ProjectException(FishErrorCode.INVALID_REFERENCE_ID);
        }
    }
}
