package com.foodservice.domain.member.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NicknameCheckResponse {

    private boolean available;

    private NicknameCheckResponse(boolean available) {
        this.available = available;
    }

    public static NicknameCheckResponse of(boolean available) {
        return new NicknameCheckResponse(available);
    }
}
