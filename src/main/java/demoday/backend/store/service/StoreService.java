package demoday.backend.store.service;

import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.store.code.StoreErrorCode;
import demoday.backend.store.dto.StoreItemResponse;
import demoday.backend.store.dto.MemberItemResponse;
import demoday.backend.store.repository.MemberItemRepository;
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
    private final MemberItemRepository memberItemRepository;

    /** 판매 중인 아이템만 조회하며 잔액이나 보유 수량은 변경하지 않는다. */
    public List<StoreItemResponse> getItems(Long memberId) {
        validateActiveMember(memberId);
        return storeItemRepository.findAllByActiveTrueOrderByItemIdAsc().stream()
                .map(StoreItemResponse::from)
                .toList();
    }

    /** 판매 여부와 관계없이 로그인 회원이 1개 이상 보유한 아이템을 조회한다. */
    public List<MemberItemResponse> getOwnedItems(Long memberId) {
        validateActiveMember(memberId);
        return memberItemRepository.findAllByMemberMemberIdAndQuantityGreaterThanOrderByItemItemIdAsc(memberId, 0)
                .stream().map(MemberItemResponse::from).toList();
    }

    private void validateActiveMember(Long memberId) {
        if (memberId == null) {
            throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw new ProjectException(StoreErrorCode.INACTIVE_MEMBER);
        }
    }
}
