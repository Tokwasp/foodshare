package com.foodservice.domain.member.dto.response;

import com.foodservice.domain.member.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginResponse {

    private Long memberId;

    private String nickName;

    private LoginResponse(Long memberId, String nickName) {
        this.memberId = memberId;
        this.nickName = nickName;
    }

    public static LoginResponse of(Member member) {
        return new LoginResponse(member.getId(), member.getNickName());
    }
}
