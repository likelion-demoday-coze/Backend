package demoday.backend.member.dto;

import demoday.backend.member.domain.Member;

import java.time.LocalDateTime;

public record OpenChatModalResponse(
        boolean shouldShow,
        LocalDateTime hiddenUntil
) {

    public static OpenChatModalResponse from(
            Member member,
            LocalDateTime now
    ) {
        LocalDateTime hiddenUntil =
                member.getOpenChatModalHiddenUntil();

        boolean shouldShow =
                hiddenUntil == null
                        || !now.isBefore(hiddenUntil);

        return new OpenChatModalResponse(
                shouldShow,
                hiddenUntil
        );
    }
}
