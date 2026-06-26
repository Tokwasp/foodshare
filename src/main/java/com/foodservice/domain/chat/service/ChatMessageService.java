package com.foodservice.domain.chat.service;

import com.foodservice.common.exception.chat.ChatRoomNotFoundException;
import com.foodservice.common.exception.chat.ForbiddenChatAccessException;
import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import com.foodservice.domain.chat.entity.ChatMessage;
import com.foodservice.domain.chat.entity.ChattingMember;
import com.foodservice.domain.chat.repository.ChatMessageRepository;
import com.foodservice.domain.chat.repository.ChatRoomRepository;
import com.foodservice.domain.chat.repository.ChattingMemberRepository;
import com.foodservice.domain.member.Member;
import com.foodservice.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChattingMemberRepository chattingMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;

    /**
     * 메시지를 정본(DB)으로 저장하고, 수신자(발신자 제외) 안읽음 수를 1 증가시킨다.
     * 실제 전달(로컬 convertAndSendToUser / 단위4 Redis 팬아웃)은 호출 측에서 {@code recipientIds}로 수행한다.
     */
    @Transactional
    public SentMessage send(Long senderId, Long roomId, String content) {
        chatRoomRepository.findById(roomId).orElseThrow(ChatRoomNotFoundException::new);

        List<ChattingMember> members = chattingMemberRepository.findByRoomId(roomId);
        boolean isParticipant = members.stream()
                .anyMatch(member -> member.getMemberId().equals(senderId));
        if (!isParticipant) {
            throw new ForbiddenChatAccessException();
        }

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content)
                .build());

        List<ChattingMember> recipients = members.stream()
                .filter(member -> !member.getMemberId().equals(senderId))
                .toList();
        recipients.forEach(ChattingMember::increaseUnreadCount);
        List<Long> recipientIds = recipients.stream()
                .map(ChattingMember::getMemberId)
                .toList();

        String senderNickName = memberRepository.findById(senderId)
                .map(Member::getNickName)
                .orElse(null);

        return new SentMessage(ChatMessagePayload.of(saved, senderNickName), recipientIds);
    }

    public record SentMessage(ChatMessagePayload payload, List<Long> recipientIds) {
    }
}
