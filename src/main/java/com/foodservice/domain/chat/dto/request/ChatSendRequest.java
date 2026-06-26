package com.foodservice.domain.chat.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * STOMP SEND `/pub/chat/rooms/{roomId}` 본문.
 */
@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class ChatSendRequest {

    private String content;
}
