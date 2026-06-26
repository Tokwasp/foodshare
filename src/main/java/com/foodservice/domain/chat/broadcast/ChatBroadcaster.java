package com.foodservice.domain.chat.broadcast;

import com.foodservice.domain.chat.dto.response.ChatMessagePayload;

import java.util.List;

/**
 * 발행된 메시지를 수신자(발신자 제외)에게 실시간 전달하는 전달 seam.
 * <ul>
 *   <li>단위 3({@link LocalChatBroadcaster}) — 같은 인스턴스의 {@code convertAndSendToUser} 직접 호출.</li>
 *   <li>단위 4({@link RedisChatBroadcaster}) — Redis 채널 {@code chat.messages}로 팬아웃 →
 *       모든 인스턴스의 리스너가 로컬 세션을 가진 수신자에게 {@code convertAndSendToUser}.</li>
 * </ul>
 * 검증·DB 저장·ACK는 호출 측({@code ChatStompController}/{@code ChatMessageService})이 담당하며,
 * 이 seam은 "전달 경로"만 책임진다. 프로파일로 빈을 교체한다(운영=Redis, test/local=Local).
 */
public interface ChatBroadcaster {

    void broadcast(ChatMessagePayload payload, List<Long> recipientIds);
}
