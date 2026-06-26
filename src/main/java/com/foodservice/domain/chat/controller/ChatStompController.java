package com.foodservice.domain.chat.controller;

import com.foodservice.domain.chat.broadcast.ChatBroadcaster;
import com.foodservice.domain.chat.dto.request.ChatSendRequest;
import com.foodservice.domain.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 실시간 메시지 발행 핸들러. SEND `/pub/chat/rooms/{roomId}`.
 * 메시지 정본 저장(+수신자 안읽음 수 증가)은 서비스가 하고, 전달은 {@link ChatBroadcaster} seam에 위임한다.
 * 전달 구현은 프로파일로 교체된다 — test/local=로컬 직접 전달, 운영=Redis 단일 채널 팬아웃.
 */
@Controller
@RequiredArgsConstructor
public class ChatStompController {

    private final ChatMessageService chatMessageService;
    private final ChatBroadcaster chatBroadcaster;

    @MessageMapping("/chat/rooms/{roomId}")
    public void send(@DestinationVariable Long roomId,
                     @Payload ChatSendRequest request,
                     Principal principal) {
        Long senderId = Long.valueOf(principal.getName());
        ChatMessageService.SentMessage sent = chatMessageService.send(senderId, roomId, request.getContent());

        chatBroadcaster.broadcast(sent.payload(), sent.recipientIds());
    }
}
