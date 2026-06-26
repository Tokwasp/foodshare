package com.foodservice.domain.chat.broadcast;

import com.foodservice.domain.chat.dto.response.ChatMessagePayload;

import java.util.List;

/**
 * Redis 채널 {@code chat.messages}로 팬아웃되는 메시지 envelope.
 * {@code payload}는 수신자에게 그대로 전달될 본문이고, {@code recipientIds}는 발신자를 제외한 수신자 목록이다.
 * 발행 인스턴스 자신도 팬아웃을 되받지만 발신자가 {@code recipientIds}에 없어 자기 메시지를 다시 받지 않는다.
 */
public record ChatBroadcastMessage(ChatMessagePayload payload, List<Long> recipientIds) {
}
