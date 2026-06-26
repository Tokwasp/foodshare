package com.foodservice.domain.chat.dto.response;

import com.foodservice.domain.chat.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

/**
 * 실시간 수신 payload (`/user/queue/messages` 구독자에게 전달).
 * REST의 ApiResponse envelope 없이 원시 payload로 나간다.
 */
@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class ChatMessagePayload {

    private static final String TYPE_CHAT_MESSAGE = "CHAT_MESSAGE";

    private String type;
    private Long messageId;
    private Long roomId;
    private Long senderId;
    private String senderNickName;
    private String content;
    private LocalDateTime createdAt;

    public static ChatMessagePayload of(ChatMessage message, String senderNickName) {
        return new ChatMessagePayload(
                TYPE_CHAT_MESSAGE,
                message.getMessageId(),
                message.getRoomId(),
                message.getSenderId(),
                senderNickName,
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
