package com.foodservice.domain.member.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailVerifyResponse {

    private boolean verified;
    private String emailVerifyToken;

    private EmailVerifyResponse(boolean verified, String emailVerifyToken) {
        this.verified = verified;
        this.emailVerifyToken = emailVerifyToken;
    }

    public static EmailVerifyResponse of(boolean verified, String emailVerifyToken) {
        return new EmailVerifyResponse(verified, emailVerifyToken);
    }
}
