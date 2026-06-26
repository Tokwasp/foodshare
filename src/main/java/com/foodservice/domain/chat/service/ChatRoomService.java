package com.foodservice.domain.chat.service;

import com.foodservice.common.exception.chat.SelfChatNotAllowedException;
import com.foodservice.common.exception.food.FoodNotAvailableException;
import com.foodservice.common.exception.food.FoodNotFoundException;
import com.foodservice.domain.chat.dto.response.ChatRoomCreateResponse;
import com.foodservice.domain.chat.entity.ChatRole;
import com.foodservice.domain.chat.entity.ChatRoom;
import com.foodservice.domain.chat.entity.ChattingMember;
import com.foodservice.domain.chat.repository.ChatRoomRepository;
import com.foodservice.domain.chat.repository.ChattingMemberRepository;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChattingMemberRepository chattingMemberRepository;
    private final FoodRepository foodRepository;

    @Transactional
    public ChatRoomCreateResponse createOrGetRoom(Long requesterId, Long foodId) {
        Food food = foodRepository.findById(foodId)
                .orElseThrow(FoodNotFoundException::new);

        if (food.getMemberId().equals(requesterId)) {
            throw new SelfChatNotAllowedException();
        }

        Optional<ChatRoom> existing = chatRoomRepository.findDirectRoom(foodId, requesterId);
        if (existing.isPresent()) {
            ChatRoom room = existing.get();
            return ChatRoomCreateResponse.of(room.getRoomId(), room.getFoodId(), false);
        }

        if (food.getExStatus() != IN_PROGRESS) {
            throw new FoodNotAvailableException();
        }

        ChatRoom room = chatRoomRepository.save(ChatRoom.builder()
                .foodId(foodId)
                .ownerId(food.getMemberId())
                .build());

        saveMember(room.getRoomId(), food.getMemberId(), ChatRole.OWNER);
        saveMember(room.getRoomId(), requesterId, ChatRole.MEMBER);

        return ChatRoomCreateResponse.of(room.getRoomId(), room.getFoodId(), true);
    }

    private void saveMember(Long roomId, Long memberId, ChatRole role) {
        chattingMemberRepository.save(ChattingMember.builder()
                .roomId(roomId)
                .memberId(memberId)
                .role(role)
                .lastReadMessageId(0L)
                .build());
    }
}
