package com.foodservice.external;

import com.foodservice.domain.chat.broadcast.ChatMessageRedisSubscriber;
import com.foodservice.domain.chat.broadcast.RedisChatBroadcaster;
import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Redis Pub/Sub <b>전파 동작</b> 검증(docs.md ⚠️ ②). mock이 아니라 실제 Redis로
 * "한 쪽 publish → 리스너 수신 → convertAndSendToUser 호출"을 확인한다.
 *
 * <p>외부 인프라(실 Redis)가 필요하므로 {@link Disabled}로 두고 수동 실행한다
 * ({@code external/RedisIntegrationTest} 패턴). 실행 전 {@code docker compose up redis}로 Redis를 띄운다.
 * 전체 Spring 컨텍스트 없이 운영 빈({@link RedisChatBroadcaster}/{@link ChatMessageRedisSubscriber})만
 * 실제 Redis 커넥션에 직접 배선한다. WS 전달부({@link SimpMessagingTemplate})는 mock으로 호출만 검증한다.
 */
@Disabled("requires a running Redis (docker compose up redis)")
class ChatRedisPubSubIntegrationTest {

    private static final String HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6380"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LettuceConnectionFactory connectionFactory;
    private RedisMessageListenerContainer container;
    private SimpMessagingTemplate messagingTemplate;
    private RedisChatBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(HOST, PORT);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();

        StringRedisTemplate redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();

        messagingTemplate = mock(SimpMessagingTemplate.class);
        ChatMessageRedisSubscriber subscriber = new ChatMessageRedisSubscriber(messagingTemplate, objectMapper);

        container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.afterPropertiesSet();
        container.addMessageListener(subscriber, new ChannelTopic(RedisChatBroadcaster.CHANNEL));
        container.start();

        broadcaster = new RedisChatBroadcaster(redisTemplate, objectMapper);
    }

    @AfterEach
    void tearDown() throws Exception {
        container.stop();
        container.destroy();
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("publish하면 채널을 구독한 리스너가 수신해 수신자에게 convertAndSendToUser로 전달한다.")
    void publishPropagatesToSubscriberAndDelivers() {
        // given
        ChatMessagePayload payload = new ChatMessagePayload(
                "CHAT_MESSAGE", 9002L, 700L, 1L, "내닉네임", "네, 가능합니다!",
                LocalDateTime.of(2025, 5, 28, 10, 6));

        // when
        broadcaster.broadcast(payload, List.of(2L));

        // then — 실제 Redis 전파는 비동기이므로 timeout으로 대기한다.
        ArgumentCaptor<Object> forwarded = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, timeout(3000))
                .convertAndSendToUser(eq("2"), eq("/queue/messages"), forwarded.capture());

        String json = objectMapper.writeValueAsString(forwarded.getValue());
        assertThat(json).contains("CHAT_MESSAGE");
        assertThat(json).contains("\"roomId\":700");
        assertThat(json).contains("네, 가능합니다!");
    }
}
