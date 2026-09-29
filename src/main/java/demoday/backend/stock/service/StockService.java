package demoday.backend.stock.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.stock.code.StockErrorCode;
import demoday.backend.stock.dto.StockResponse;
import demoday.backend.stock.dto.StockChangePageResponse;
import demoday.backend.stock.repository.StockChangeRepository;
import demoday.backend.stock.repository.StockDailySnapshotRepository;
import demoday.backend.stock.dto.StockHistoryResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StockService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DEFAULT_HISTORY_DAYS = 30;
    private static final int MAX_HISTORY_DAYS = 366;

    private static final Sort CHANGE_SORT = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("stockChangeId")
    );

    private final MemberRepository memberRepository;
    private final StockChangeRepository stockChangeRepository;
    private final StockDailySnapshotRepository stockDailySnapshotRepository;
    private final Clock clock;

    public StockHistoryResponse getHistory(Long memberId, LocalDate from, LocalDate to) {
        findActiveMember(memberId);
        if (from == null && to == null) {
            to = LocalDate.now(clock.withZone(KST));
            from = to.minusDays(DEFAULT_HISTORY_DAYS - 1);
        } else if (from == null || to == null) {
            throw new ProjectException(StockErrorCode.INVALID_HISTORY_PERIOD);
        }
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) >= MAX_HISTORY_DAYS) {
            throw new ProjectException(StockErrorCode.INVALID_HISTORY_PERIOD);
        }
        return StockHistoryResponse.from(from, to,
                stockDailySnapshotRepository.findAllByMemberMemberIdAndSnapshotDateBetweenOrderBySnapshotDateAsc(
                        memberId, from, to
                ));
    }

    public StockResponse getCurrentStock(Long memberId) {
        return new StockResponse(findActiveMember(memberId).getCurrentStock());
    }

    public StockChangePageResponse getChanges(Long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ProjectException(StockErrorCode.INVALID_PAGE);
        }
        findActiveMember(memberId);
        return StockChangePageResponse.from(stockChangeRepository.findAllByMemberMemberId(
                memberId, PageRequest.of(page, size, CHANGE_SORT)
        ));
    }

    private Member findActiveMember(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(StockErrorCode.INACTIVE_MEMBER);
        }
        return member;
    }
}
