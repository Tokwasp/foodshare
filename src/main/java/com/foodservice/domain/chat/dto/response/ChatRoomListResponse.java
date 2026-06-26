package com.foodservice.domain.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class ChatRoomListResponse {

    private Long roomId;
    private Long foodId;
    private String foodName;
    private String partnerNickName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private long unreadCount;

    public static ChatRoomListResponse of(Long roomId, Long foodId, String foodName, String partnerNickName,
                                          String lastMessage, LocalDateTime lastMessageAt, long unreadCount) {
        return new ChatRoomListResponse(roomId, foodId, foodName, partnerNickName,
                lastMessage, lastMessageAt, unreadCount);
    }
}
