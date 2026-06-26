package com.foodservice.domain.member;

import com.foodservice.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long id;

    private String nickName;

    private String email;

    private String password;

    @Embedded
    private Address address;

    @Builder
    private Member(String nickName, String email, String password, Address address) {
        this.nickName = nickName;
        this.email = email;
        this.password = password;
        this.address = address;
    }

    public boolean isNotMatchPassword(PasswordEncoder passwordEncoder, String rawPassword) {
        return !passwordEncoder.matches(rawPassword, this.password);
    }

    public void changeNickName(String nickName) {
        this.nickName = nickName;
    }

    public void changeAddress(Address address) {
        this.address = address;
    }
}