package com.foodservice.domain.member.dto.request;

import com.foodservice.domain.member.Address;
import com.foodservice.domain.member.Member;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberCreateServiceRequest {

    private String email;
    private String emailVerifyToken;
    private String password;
    private String nickName;
    private String roadAddress;
    private String detailAddress;

    @Builder
    private MemberCreateServiceRequest(String email, String emailVerifyToken, String password,
                                       String nickName, String roadAddress, String detailAddress) {
        this.email = email;
        this.emailVerifyToken = emailVerifyToken;
        this.password = password;
        this.nickName = nickName;
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
    }

    public Member toEntity(String encodedPassword) {
        return Member.builder()
                .email(email)
                .nickName(nickName)
                .password(encodedPassword)
                .address(Address.of(roadAddress, detailAddress))
                .build();
    }
}
