package com.foodservice.domain.chat.repository;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.domain.chat.entity.ChatRole;
import com.foodservice.domain.chat.entity.ChatRoom;
import com.foodservice.domain.chat.entity.ChattingMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ChatRoomRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChattingMemberRepository chattingMemberRepository;

    @Test
    @DisplayName("물품과 신청자 멤버십으로 기존 1:1 채팅방을 찾는다.")
    void findDirectRoomReturnsRoomWhenMemberParticipates() {
        // given
        Long foodId = 100L;
        Long ownerId = 1L;
        Long requesterId = 2L;
        ChatRoom room = saveRoom(foodId, ownerId);
        saveMember(room.getRoomId(), ownerId, ChatRole.OWNER);
        saveMember(room.getRoomId(), requesterId, ChatRole.MEMBER);

        // when
        Optional<ChatRoom> found = chatRoomRepository.findDirectRoom(foodId, requesterId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getRoomId()).isEqualTo(room.getRoomId());
    }

    @Test
    @DisplayName("해당 물품 방에 참여하지 않은 회원으로 조회하면 빈 결과를 반환한다.")
    void findDirectRoomReturnsEmptyWhenNotParticipant() {
        // given
        Long foodId = 100L;
        ChatRoom room = saveRoom(foodId, 1L);
        saveMember(room.getRoomId(), 1L, ChatRole.OWNER);
        saveMember(room.getRoomId(), 2L, ChatRole.MEMBER);

        // when
        Optional<ChatRoom> found = chatRoomRepository.findDirectRoom(foodId, 999L);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("다른 물품의 방은 조회되지 않는다.")
    void findDirectRoomReturnsEmptyForDifferentFood() {
        // given
        ChatRoom room = saveRoom(100L, 1L);
        saveMember(room.getRoomId(), 2L, ChatRole.MEMBER);

        // when
        Optional<ChatRoom> found = chatRoomRepository.findDirectRoom(200L, 2L);

        // then
        assertThat(found).isEmpty();
    }

    private ChatRoom saveRoom(Long foodId, Long ownerId) {
        return chatRoomRepository.save(ChatRoom.builder()
                .foodId(foodId)
                .ownerId(ownerId)
                .build());
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
