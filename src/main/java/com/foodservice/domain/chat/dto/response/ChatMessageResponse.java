package com.foodservice.domain.chat.dto.response;

import com.foodservice.domain.chat.entity.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class ChatMessageResponse {

    private Long messageId;
    private Long senderId;
    private String senderNickName;
    private String content;
    private boolean mine;
    private LocalDateTime createdAt;

    public static ChatMessageResponse of(ChatMessage message, String senderNickName, Long loginMemberId) {
        return new ChatMessageResponse(
                message.getMessageId(),
                message.getSenderId(),
                senderNickName,
                message.getContent(),
                message.getSenderId().equals(loginMemberId),
                message.getCreatedAt()
        );
    }
}
