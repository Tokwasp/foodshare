package com.foodservice.domain.chat.repository;

import com.foodservice.domain.chat.entity.ChatMessage;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query("select m from ChatMessage m where m.roomId = :roomId order by m.messageId desc")
    List<ChatMessage> findLatestMessages(@Param("roomId") Long roomId, Limit limit);

    @Query("select m from ChatMessage m where m.roomId = :roomId and m.messageId < :cursor order by m.messageId desc")
    List<ChatMessage> findMessagesBefore(@Param("roomId") Long roomId, @Param("cursor") Long cursor, Limit limit);

    @Query("select m from ChatMessage m where m.roomId = :roomId and m.messageId <= :anchor order by m.messageId desc")
    List<ChatMessage> findMessagesAtOrBefore(@Param("roomId") Long roomId, @Param("anchor") Long anchor, Limit limit);

    @Query("select m from ChatMessage m where m.roomId = :roomId and m.messageId > :cursor order by m.messageId asc")
    List<ChatMessage> findMessagesAfter(@Param("roomId") Long roomId, @Param("cursor") Long cursor, Limit limit);

    boolean existsByRoomIdAndMessageIdLessThan(Long roomId, Long messageId);

    boolean existsByRoomIdAndMessageIdGreaterThan(Long roomId, Long messageId);

    @Query("select m from ChatMessage m where m.messageId in (select max(m2.messageId) from ChatMessage m2 where m2.roomId in :roomIds group by m2.roomId)")
    List<ChatMessage> findLastMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);
}
