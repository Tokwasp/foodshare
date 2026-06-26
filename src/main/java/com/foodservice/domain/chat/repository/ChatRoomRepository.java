package com.foodservice.domain.chat.repository;

import com.foodservice.domain.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("select cr from ChatRoom cr, ChattingMember cm "
            + "where cm.roomId = cr.roomId "
            + "and cr.foodId = :foodId "
            + "and cm.memberId = :memberId "
            + "and cr.deleted = false")
    Optional<ChatRoom> findDirectRoom(@Param("foodId") Long foodId, @Param("memberId") Long memberId);
}
