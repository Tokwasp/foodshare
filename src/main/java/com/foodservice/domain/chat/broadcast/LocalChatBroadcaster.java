package com.foodservice.domain.chat.broadcast;

import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 단일 인스턴스 전달(단위 3). 같은 서버의 로컬 SimpleBroker로 직접 전달한다.
 * Redis가 없는 test/local 프로파일에서 사용한다(운영=분산 {@link RedisChatBroadcaster}).
 */
@Component
@Profile({"test", "local"})
@RequiredArgsConstructor
public class LocalChatBroadcaster implements ChatBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void broadcast(ChatMessagePayload payload, List<Long> recipientIds) {
        for (Long recipientId : recipientIds) {
            messagingTemplate.convertAndSendToUser(String.valueOf(recipientId), "/queue/messages", payload);
        }
    }
}
