package com.foodservice.domain.chat.service;

import com.foodservice.common.exception.chat.ChatRoomNotFoundException;
import com.foodservice.common.exception.chat.ForbiddenChatAccessException;
import com.foodservice.domain.chat.dto.response.ChatHistoryResponse;
import com.foodservice.domain.chat.dto.response.ChatMessageResponse;
import com.foodservice.domain.chat.dto.response.ChatRoomListResponse;
import com.foodservice.domain.chat.entity.ChatMessage;
import com.foodservice.domain.chat.entity.ChatRoom;
import com.foodservice.domain.chat.entity.ChattingMember;
import com.foodservice.domain.chat.repository.ChatMessageRepository;
import com.foodservice.domain.chat.repository.ChatRoomRepository;
import com.foodservice.domain.chat.repository.ChattingMemberRepository;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import com.foodservice.domain.member.Member;
import com.foodservice.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatHistoryService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChattingMemberRepository chattingMemberRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final MemberRepository memberRepository;
    private final FoodRepository foodRepository;

    public List<ChatRoomListResponse> getMyRooms(Long memberId) {
        List<ChattingMember> myMemberships = chattingMemberRepository.findByMemberId(memberId);
        if (myMemberships.isEmpty()) {
            return List.of();
        }
        List<Long> roomIds = myMemberships.stream().map(ChattingMember::getRoomId).toList();

        Map<Long, ChatRoom> roomById = indexBy(chatRoomRepository.findAllById(roomIds), ChatRoom::getRoomId);
        Map<Long, Long> partnerIdByRoom = partnerIdsByRoom(roomIds, memberId);
        Map<Long, String> nickById = indexBy(memberRepository.findAllById(partnerIdByRoom.values()), Member::getId, Member::getNickName);
        Map<Long, String> foodNameById = indexBy(foodRepository.findAllById(foodIdsOf(roomById)), Food::getFoodId, Food::getFoodName);
        Map<Long, ChatMessage> lastMsgByRoom = indexBy(chatMessageRepository.findLastMessagesByRoomIds(roomIds), ChatMessage::getRoomId);

        return myMemberships.stream()
                .map(cm -> toRoomListResponse(cm, roomById, partnerIdByRoom, nickById, foodNameById, lastMsgByRoom))
                .sorted(Comparator.comparing(ChatRoomListResponse::getLastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static final String DIRECTION_BEFORE = "before";
    private static final String DIRECTION_AFTER = "after";

    @Transactional
    public ChatHistoryResponse getMessages(Long memberId, Long roomId, String direction, Long cursor, int size) {
        chatRoomRepository.findById(roomId).orElseThrow(ChatRoomNotFoundException::new);
        ChattingMember myMembership = chattingMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
                .orElseThrow(ForbiddenChatAccessException::new);

        if (DIRECTION_BEFORE.equalsIgnoreCase(direction)) {
            return buildResponse(roomId, chatMessageRepository.findMessagesBefore(roomId, cursor, Limit.of(size)), null, memberId);
        }
        if (DIRECTION_AFTER.equalsIgnoreCase(direction)) {
            return buildResponse(roomId, toDesc(chatMessageRepository.findMessagesAfter(roomId, cursor, Limit.of(size))), null, memberId);
        }
        return initial(roomId, myMembership, size, memberId);
    }

    private ChatHistoryResponse initial(Long roomId, ChattingMember myMembership, int size, Long memberId) {
        Long anchor = myMembership.getLastReadMessageId();

        // 위쪽: 마지막 읽은 메시지(포함) 이하 최신순 size개, 아래쪽: 그 다음부터 오래된순 size개 → 내림차순 병합
        List<ChatMessage> upDesc = chatMessageRepository.findMessagesAtOrBefore(roomId, anchor, Limit.of(size));
        List<ChatMessage> downDesc = toDesc(chatMessageRepository.findMessagesAfter(roomId, anchor, Limit.of(size)));
        List<ChatMessage> merged = new ArrayList<>(downDesc.size() + upDesc.size());
        merged.addAll(downDesc);
        merged.addAll(upDesc);

        markAllRead(roomId, myMembership);
        return buildResponse(roomId, merged, anchor, memberId);
    }

    private void markAllRead(Long roomId, ChattingMember myMembership) {
        chatMessageRepository.findLatestMessages(roomId, Limit.of(1)).stream()
                .findFirst()
                .ifPresent(latest -> myMembership.updateLastReadMessageId(latest.getMessageId()));
        myMembership.resetUnreadCount();
    }

    private ChatHistoryResponse buildResponse(Long roomId, List<ChatMessage> descMessages, Long anchorMessageId, Long memberId) {
        if (descMessages.isEmpty()) {
            return ChatHistoryResponse.of(List.of(), anchorMessageId, null, null, false, false);
        }
        Long downCursor = descMessages.get(0).getMessageId();
        Long upCursor = descMessages.get(descMessages.size() - 1).getMessageId();
        boolean hasPrev = chatMessageRepository.existsByRoomIdAndMessageIdLessThan(roomId, upCursor);
        boolean hasNext = chatMessageRepository.existsByRoomIdAndMessageIdGreaterThan(roomId, downCursor);
        return ChatHistoryResponse.of(toMessageResponses(descMessages, memberId), anchorMessageId, upCursor, downCursor, hasPrev, hasNext);
    }

    private List<ChatMessage> toDesc(List<ChatMessage> ascMessages) {
        List<ChatMessage> desc = new ArrayList<>(ascMessages);
        Collections.reverse(desc);
        return desc;
    }

    private List<ChatMessageResponse> toMessageResponses(List<ChatMessage> page, Long memberId) {
        Map<Long, String> nickNames = nickNamesOf(page);
        return page.stream()
                .map(m -> ChatMessageResponse.of(m, nickNames.get(m.getSenderId()), memberId))
                .toList();
    }

    private Map<Long, String> nickNamesOf(List<ChatMessage> messages) {
        List<Long> senderIds = messages.stream()
                .map(ChatMessage::getSenderId)
                .distinct()
                .toList();
        return indexBy(memberRepository.findAllById(senderIds), Member::getId, Member::getNickName);
    }

    private Map<Long, Long> partnerIdsByRoom(List<Long> roomIds, Long memberId) {
        return chattingMemberRepository.findByRoomIdIn(roomIds).stream()
                .filter(member -> !member.getMemberId().equals(memberId))
                .collect(Collectors.toMap(ChattingMember::getRoomId, ChattingMember::getMemberId, (a, b) -> a));
    }

    private static <T, K> Map<K, T> indexBy(List<T> items, Function<T, K> keyMapper) {
        return indexBy(items, keyMapper, Function.identity());
    }

    private static <T, K, V> Map<K, V> indexBy(List<T> items, Function<T, K> keyMapper, Function<T, V> valueMapper) {
        return items.stream().collect(Collectors.toMap(keyMapper, valueMapper, (a, b) -> a));
    }

    private List<Long> foodIdsOf(Map<Long, ChatRoom> roomById) {
        return roomById.values().stream()
                .map(ChatRoom::getFoodId)
                .distinct()
                .toList();
    }

    private ChatRoomListResponse toRoomListResponse(ChattingMember myMembership,
                                                    Map<Long, ChatRoom> roomById,
                                                    Map<Long, Long> partnerIdByRoom,
                                                    Map<Long, String> nickById,
                                                    Map<Long, String> foodNameById,
                                                    Map<Long, ChatMessage> lastMsgByRoom) {
        Long roomId = myMembership.getRoomId();
        Long foodId = roomById.get(roomId).getFoodId();
        ChatMessage lastMessage = lastMsgByRoom.get(roomId);

        return ChatRoomListResponse.of(
                roomId,
                foodId,
                foodNameById.get(foodId),
                nickById.get(partnerIdByRoom.get(roomId)),
                lastMessage != null ? lastMessage.getContent() : null,
                lastMessage != null ? lastMessage.getCreatedAt() : null,
                myMembership.getUnreadCount()
        );
    }
}
