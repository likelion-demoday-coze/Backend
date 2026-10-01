package demoday.backend.store.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.store.code.StoreErrorCode;
import demoday.backend.store.dto.StoreItemResponse;
import demoday.backend.store.repository.StoreItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreService {
    private final MemberRepository memberRepository;
    private final StoreItemRepository storeItemRepository;

    /** 판매 중인 아이템만 조회하며 잔액이나 보유 수량은 변경하지 않는다. */
    public List<StoreItemResponse> getItems(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(StoreErrorCode.INACTIVE_MEMBER);
        }
        return storeItemRepository.findAllByActiveTrueOrderByItemIdAsc().stream()
                .map(StoreItemResponse::from)
                .toList();
    }
}
