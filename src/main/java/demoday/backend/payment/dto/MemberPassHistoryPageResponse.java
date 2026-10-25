package demoday.backend.payment.dto;

import demoday.backend.payment.domain.MemberPass;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

public record MemberPassHistoryPageResponse(
        List<MemberPassHistoryResponse> passes,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static MemberPassHistoryPageResponse from(
            Page<MemberPass> result,
            LocalDateTime now
    ) {
        return new MemberPassHistoryPageResponse(
                result.getContent().stream()
                        .map(memberPass ->
                                MemberPassHistoryResponse.from(
                                        memberPass,
                                        now
                                )
                        )
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
