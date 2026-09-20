package demoday.backend.member.dto;

import demoday.backend.member.domain.Member;

public record MemberResponse(Long memberId, String nickname) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getMemberId(), member.getNickname());
    }
}
