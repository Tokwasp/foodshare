package com.foodservice.domain.chat.broadcast;

import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class LocalChatBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private LocalChatBroadcaster broadcaster;

    @Test
    @DisplayName("수신자마다 로컬 /user/queue/messages로 payload를 전달한다.")
    void broadcastsToEachRecipientUserQueue() {
        // given
        ChatMessagePayload payload = payload();

        // when
        broadcaster.broadcast(payload, List.of(2L, 3L));

        // then
        verify(messagingTemplate).convertAndSendToUser("2", "/queue/messages", payload);
        verify(messagingTemplate).convertAndSendToUser("3", "/queue/messages", payload);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("수신자가 없으면(발신자만 있는 방) 아무것도 전달하지 않는다.")
    void broadcastsNothingWhenNoRecipients() {
        // when
        broadcaster.broadcast(payload(), List.of());

        // then
        verifyNoMoreInteractions(messagingTemplate);
    }

    private ChatMessagePayload payload() {
        return new ChatMessagePayload("CHAT_MESSAGE", 9002L, 700L, 1L, "내닉네임", "네, 가능합니다!",
                LocalDateTime.of(2025, 5, 28, 10, 6));
    }
}
