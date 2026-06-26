package com.foodservice.domain.chat.repository;

import com.foodservice.domain.chat.entity.ChattingMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChattingMemberRepository extends JpaRepository<ChattingMember, Long> {

    List<ChattingMember> findByRoomId(Long roomId);

    List<ChattingMember> findByRoomIdIn(List<Long> roomIds);

    List<ChattingMember> findByMemberId(Long memberId);

    Optional<ChattingMember> findByRoomIdAndMemberId(Long roomId, Long memberId);
}
