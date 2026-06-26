package com.foodservice.domain.chat.broadcast;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Redis 채널 {@code chat.messages} 구독자. 모든 인스턴스가 이 리스너를 등록한다(발행 인스턴스 포함).
 * 수신한 envelope의 {@code recipientIds}마다 {@code convertAndSendToUser}로 로컬 전달을 시도한다.
 * 수신자 세션을 로컬에 가진 인스턴스만 실제 push하고, 없는 인스턴스는 Spring user 레지스트리가 못 찾아 no-op이다.
 *
 * <p>{@code payload}는 역직렬화로 타입을 복원하지 않고 JSON 트리 그대로 전달한다 —
 * 메시지 컨버터가 동일 JSON으로 다시 직렬화하므로 발행 시점 payload와 와이어 형식이 일치한다.
 */
@Component
@Profile("!test & !local")
@RequiredArgsConstructor
public class ChatMessageRedisSubscriber implements MessageListener {

    private static final String USER_QUEUE = "/queue/messages";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        JsonNode root = objectMapper.readTree(message.getBody());
        JsonNode payload = root.get("payload");
        for (JsonNode recipientId : root.get("recipientIds")) {
            messagingTemplate.convertAndSendToUser(String.valueOf(recipientId.asLong()), USER_QUEUE, payload);
        }
    }
}
