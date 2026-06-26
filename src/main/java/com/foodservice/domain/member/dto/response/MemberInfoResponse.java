package com.foodservice.domain.member.dto.response;

import com.foodservice.domain.member.Address;
import com.foodservice.domain.member.Member;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberInfoResponse {

    private Long memberId;

    private String email;

    private String nickName;

    private AddressResponse address;

    private LocalDateTime createdAt;

    private MemberInfoResponse(Long memberId, String email, String nickName,
                              AddressResponse address, LocalDateTime createdAt) {
        this.memberId = memberId;
        this.email = email;
        this.nickName = nickName;
        this.address = address;
        this.createdAt = createdAt;
    }

    public static MemberInfoResponse of(Member member) {
        return new MemberInfoResponse(
                member.getId(),
                member.getEmail(),
                member.getNickName(),
                AddressResponse.of(member.getAddress()),
                member.getCreatedAt()
        );
    }

    @Getter
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class AddressResponse {

        private String roadAddress;

        private String detailAddress;

        private AddressResponse(String roadAddress, String detailAddress) {
            this.roadAddress = roadAddress;
            this.detailAddress = detailAddress;
        }

        private static AddressResponse of(Address address) {
            return new AddressResponse(address.getRoadAddress(), address.getDetailAddress());
        }
    }
}
