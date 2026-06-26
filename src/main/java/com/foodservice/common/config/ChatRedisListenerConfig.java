package com.foodservice.common.config;

import com.foodservice.domain.chat.broadcast.ChatMessageRedisSubscriber;
import com.foodservice.domain.chat.broadcast.RedisChatBroadcaster;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * 분산 전달(단위 4)용 Redis Pub/Sub 구독 설정. 운영(default) 프로파일에서만 활성화한다.
 * 단일 채널 {@code chat.messages}를 {@link ChatMessageRedisSubscriber}로 구독한다.
 * test/local 프로파일에서는 로딩되지 않으므로 실제 Redis 없이 기존 단위 3 통합테스트가 그대로 돈다.
 */
@Configuration
@Profile("!test & !local")
public class ChatRedisListenerConfig {

    @Bean
    public RedisMessageListenerContainer chatRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatMessageRedisSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(RedisChatBroadcaster.CHANNEL));
        return container;
    }
}
