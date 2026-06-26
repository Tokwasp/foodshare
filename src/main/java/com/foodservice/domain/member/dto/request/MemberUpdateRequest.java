package com.foodservice.domain.member.dto.request;

import com.foodservice.domain.member.Address;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberUpdateRequest {

    @Size(min = 2, max = 10, message = "닉네임은 2~10자입니다.")
    private String nickName;

    @Valid
    private AddressRequest address;

    @Builder
    private MemberUpdateRequest(String nickName, AddressRequest address) {
        this.nickName = nickName;
        this.address = address;
    }

    public Address toAddress() {
        if (address == null) {
            return null;
        }
        return Address.of(address.getRoadAddress(), address.getDetailAddress());
    }
}
