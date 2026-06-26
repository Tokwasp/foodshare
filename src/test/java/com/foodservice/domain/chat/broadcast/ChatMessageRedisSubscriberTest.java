package com.foodservice.domain.chat.broadcast;

import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageRedisSubscriberTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChatMessageRedisSubscriber subscriber;

    @BeforeEach
    void setUp() {
        subscriber = new ChatMessageRedisSubscriber(messagingTemplate, objectMapper);
    }

    @Test
    @DisplayName("채널에서 받은 envelope의 수신자마다 /user/queue/messages로 payload를 전달한다.")
    void deliversPayloadToEachRecipient() {
        // given
        ChatMessagePayload payload = payload();
        Message message = messageOf(new ChatBroadcastMessage(payload, List.of(2L, 3L)));

        // when
        subscriber.onMessage(message, null);

        // then
        ArgumentCaptor<Object> forwarded = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSendToUser(eq("2"), eq("/queue/messages"), forwarded.capture());
        verify(messagingTemplate).convertAndSendToUser(eq("3"), eq("/queue/messages"), any());
        verifyNoMoreInteractions(messagingTemplate);

        // 전달한 payload를 다시 직렬화하면 발행 시점 payload 필드를 그대로 담고 있다.
        String json = objectMapper.writeValueAsString(forwarded.getValue());
        assertThat(json).contains("CHAT_MESSAGE");
        assertThat(json).contains("\"messageId\":9002");
        assertThat(json).contains("\"roomId\":700");
        assertThat(json).contains("네, 가능합니다!");
    }

    @Test
    @DisplayName("수신자가 비어 있으면 아무에게도 전달하지 않는다.")
    void deliversNothingWhenNoRecipients() {
        // given
        Message message = messageOf(new ChatBroadcastMessage(payload(), List.of()));

        // when
        subscriber.onMessage(message, null);

        // then
        verifyNoMoreInteractions(messagingTemplate);
    }

    private Message messageOf(ChatBroadcastMessage broadcast) {
        String body = objectMapper.writeValueAsString(broadcast);
        Message message = mock(Message.class);
        given(message.getBody()).willReturn(body.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private ChatMessagePayload payload() {
        return new ChatMessagePayload("CHAT_MESSAGE", 9002L, 700L, 1L, "내닉네임", "네, 가능합니다!",
                LocalDateTime.of(2025, 5, 28, 10, 6));
    }
}
