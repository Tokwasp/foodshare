package com.foodservice.domain.member.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSendResponse {

    private int expiresIn;

    private EmailSendResponse(int expiresIn) {
        this.expiresIn = expiresIn;
    }

    public static EmailSendResponse of(int expiresIn) {
        return new EmailSendResponse(expiresIn);
    }
}
