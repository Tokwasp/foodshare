package com.foodservice.domain.member.dto.response;

import com.foodservice.domain.member.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCreateResponse {

    private Long memberId;

    private MemberCreateResponse(Long memberId) {
        this.memberId = memberId;
    }

    public static MemberCreateResponse of(Member member) {
        return new MemberCreateResponse(member.getId());
    }
}
