package com.foodservice.domain.chat.broadcast;

import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 분산 전달(단위 4). 발행 메시지를 Redis 단일 채널 {@code chat.messages}로 팬아웃한다.
 * 모든 인스턴스가 이 채널을 구독하며, 수신자 WS 세션을 로컬에 가진 인스턴스만 실제 push한다
 * (나머지는 Spring user 레지스트리가 못 찾아 자동 no-op). presence·워커·DLX는 두지 않는다.
 *
 * <p>전달은 best-effort다. 메시지 정본은 이미 DB에 저장되어 있으므로 Redis 단절·구독자 부재 시에도 유실이 아니다.
 */
@Component
@Profile("!test & !local")
@RequiredArgsConstructor
public class RedisChatBroadcaster implements ChatBroadcaster {

    /** 모든 인스턴스가 구독하는 단일 팬아웃 채널. */
    public static final String CHANNEL = "chat.messages";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void broadcast(ChatMessagePayload payload, List<Long> recipientIds) {
        String message = objectMapper.writeValueAsString(new ChatBroadcastMessage(payload, recipientIds));
        redisTemplate.convertAndSend(CHANNEL, message);
    }
}
