package com.foodservice.domain.member.repository;

import com.foodservice.domain.member.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    boolean existsByNickName(String nickName);

    Optional<Member> findByEmail(String email);
}
