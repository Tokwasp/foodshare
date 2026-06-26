package com.foodservice.domain.member;

import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address {

    private String roadAddress;

    private String detailAddress;

    private Address(String roadAddress, String detailAddress) {
        this.roadAddress = roadAddress;
        this.detailAddress = detailAddress;
    }

    public static Address of(String roadAddress, String detailAddress) {
        return new Address(roadAddress, detailAddress);
    }
}
