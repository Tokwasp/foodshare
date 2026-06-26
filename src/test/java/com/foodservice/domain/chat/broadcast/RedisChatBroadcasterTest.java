package com.foodservice.domain.chat.broadcast;

import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisChatBroadcasterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RedisChatBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new RedisChatBroadcaster(redisTemplate, objectMapper);
    }

    @Test
    @DisplayName("단일 채널 chat.messages로 (payload, recipientIds) envelope를 publish한다.")
    void publishesEnvelopeToSingleChannel() {
        // given
        ChatMessagePayload payload = payload();

        // when
        broadcaster.broadcast(payload, List.of(2L));

        // then
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("chat.messages"), body.capture());

        String published = body.getValue();
        assertThat(published).contains("\"recipientIds\":[2]");
        assertThat(published).contains("CHAT_MESSAGE");
        assertThat(published).contains("\"messageId\":9002");
        assertThat(published).contains("\"roomId\":700");
        assertThat(published).contains("\"senderId\":1");
        assertThat(published).contains("네, 가능합니다!");
    }

    @Test
    @DisplayName("호출자가 넘긴 수신자 목록(발신자 제외)을 그대로 envelope에 담는다.")
    void carriesGivenRecipientIds() {
        // when
        broadcaster.broadcast(payload(), List.of(2L, 3L));

        // then
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq("chat.messages"), body.capture());
        assertThat(body.getValue()).contains("\"recipientIds\":[2,3]");
    }

    private ChatMessagePayload payload() {
        return new ChatMessagePayload("CHAT_MESSAGE", 9002L, 700L, 1L, "내닉네임", "네, 가능합니다!",
                LocalDateTime.of(2025, 5, 28, 10, 6));
    }
}
