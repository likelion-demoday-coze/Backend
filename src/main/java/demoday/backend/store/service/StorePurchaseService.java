package demoday.backend.store.service;

import demoday.backend.fish.code.FishTransactionType;
import demoday.backend.fish.service.FishService;
import demoday.backend.global.api.code.GeneralErrorCode;
import demoday.backend.global.exception.ProjectException;
import demoday.backend.global.transaction.TransactionRetryExecutor;
import demoday.backend.member.code.MemberErrorCode;
import demoday.backend.member.code.MemberStatus;
import demoday.backend.member.domain.Member;
import demoday.backend.member.repository.MemberRepository;
import demoday.backend.store.code.StoreErrorCode;
import demoday.backend.store.domain.*;
import demoday.backend.store.dto.*;
import demoday.backend.store.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class StorePurchaseService {
    private final MemberRepository members;
    private final StoreItemRepository items;
    private final MemberItemRepository inventory;
    private final StorePurchaseRepository purchases;
    private final FishService fish;
    private final TransactionRetryExecutor retryExecutor;
    private final Clock clock;

    /** 동일 구매 요청을 다시 보내도 잔액과 수량을 중복 변경하지 않는다. */
    public StorePurchaseResponse purchase(Long memberId, Long itemId, StorePurchaseRequest request) {
        if (request == null || request.quantity() == null || request.quantity() <= 0 || request.requestId() == null
                || itemId == null || itemId <= 0) throw new ProjectException(StoreErrorCode.INVALID_PURCHASE);
        if (memberId == null) throw new ProjectException(GeneralErrorCode.UNAUTHORIZED);
        return retryExecutor.execute(() -> purchaseInTransaction(memberId, itemId, request));
    }

    private StorePurchaseResponse purchaseInTransaction(Long memberId, Long itemId, StorePurchaseRequest request) {
        // 첫 조회부터 회원 잠금. 같은 회원의 구매·차감·수량 변경을 직렬화한다.
        Member member = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new ProjectException(GeneralErrorCode.NOT_FOUND));
        if (member.getStatus() != MemberStatus.ACTIVE) throw new ProjectException(StoreErrorCode.INACTIVE_MEMBER);
        String requestId = request.requestId().toString();
        StorePurchase existing = purchases.findByMemberMemberIdAndRequestId(memberId, requestId).orElse(null);
        if (existing != null) {
            if (!existing.getItem().getItemId().equals(itemId) || existing.getQuantity() != request.quantity().intValue())
                throw new ProjectException(StoreErrorCode.PURCHASE_CONFLICT);
            return StorePurchaseResponse.from(existing);
        }
        StoreItem item = items.findById(itemId)
                .orElseThrow(() -> new ProjectException(StoreErrorCode.ITEM_NOT_FOUND));
        if (!Boolean.TRUE.equals(item.getActive())) throw new ProjectException(StoreErrorCode.ITEM_UNAVAILABLE);
        MemberItem owned = inventory.findByMemberMemberIdAndItemItemId(memberId, itemId).orElse(null);
        int currentQuantity = owned == null ? 0 : owned.getQuantity();
        if (currentQuantity > Integer.MAX_VALUE - request.quantity())
            throw new ProjectException(StoreErrorCode.QUANTITY_OVERFLOW);
        long totalPrice = (long) item.getFishPrice() * request.quantity();
        if (member.getFishBalance() < totalPrice) throw new ProjectException(MemberErrorCode.INSUFFICIENT_FISH);
        StorePurchase purchase = purchases.save(StorePurchase.create(member, item, requestId, request.quantity(),
                totalPrice, member.getFishBalance() - totalPrice, currentQuantity + request.quantity(),
                LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))).truncatedTo(ChronoUnit.MICROS)));
        // 0원 상품도 도메인에서 허용한다. 이 경우 0개 차감 거래는 만들지 않는다.
        if (totalPrice > 0) fish.debit(memberId, totalPrice, FishTransactionType.ITEM_PURCHASE,
                purchase.getPurchaseId(), "STORE_PURCHASE:" + memberId + ":" + requestId);
        if (owned == null) inventory.save(MemberItem.create(member, item, request.quantity()));
        else owned.addQuantity(request.quantity());
        return StorePurchaseResponse.from(purchase);
    }
}
