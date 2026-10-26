package demoday.backend.payment.dto;

import demoday.backend.payment.domain.MemberPass;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record CurrentMemberPassResponse(

        @Schema(description = "현재 사용 가능한 패스 보유 여부", example = "true")
        boolean active,

        @Schema(
                description = "현재 활성 패스. 활성 패스가 없으면 null",
                nullable = true
        )
        MemberPassResponse pass
) {

    public static CurrentMemberPassResponse inactive() {
        return new CurrentMemberPassResponse(false, null);
    }

    public static CurrentMemberPassResponse active(
            MemberPass memberPass,
            LocalDateTime now
    ) {
        return new CurrentMemberPassResponse(
                true,
                MemberPassResponse.from(memberPass, now)
        );
    }
}
