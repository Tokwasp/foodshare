package com.foodservice.domain.chat.service;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.exception.chat.ChatRoomNotFoundException;
import com.foodservice.common.exception.chat.ForbiddenChatAccessException;
import com.foodservice.domain.chat.dto.response.ChatMessagePayload;
import com.foodservice.domain.chat.entity.ChatRole;
import com.foodservice.domain.chat.entity.ChatRoom;
import com.foodservice.domain.chat.entity.ChatMessage;
import com.foodservice.domain.chat.entity.ChattingMember;
import com.foodservice.domain.chat.repository.ChatMessageRepository;
import com.foodservice.domain.chat.repository.ChatRoomRepository;
import com.foodservice.domain.chat.repository.ChattingMemberRepository;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import com.foodservice.domain.member.Member;
import com.foodservice.domain.member.repository.MemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatMessageServiceTest extends IntegrationTestSupport {

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChattingMemberRepository chattingMemberRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("메시지를 보내면 ChatMessage를 DB에 정본으로 저장하고, 발신자 닉네임을 채운 payload를 반환한다.")
    void send() {
        // given
        Long ownerId = saveMember("등록자");
        Long requester = saveMember("신청자");
        Long foodId = saveFood(ownerId, "미개봉 시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveChattingMember(roomId, ownerId, ChatRole.OWNER, 0L);
        saveChattingMember(roomId, requester, ChatRole.MEMBER, 0L);

        // when
        ChatMessageService.SentMessage sent =
                chatMessageService.send(requester, roomId, "안녕하세요, 나눔 가능할까요?");

        // then
        ChatMessagePayload payload = sent.payload();
        assertThat(payload.getType()).isEqualTo("CHAT_MESSAGE");
        assertThat(payload.getMessageId()).isNotNull();
        assertThat(payload.getRoomId()).isEqualTo(roomId);
        assertThat(payload.getSenderId()).isEqualTo(requester);
        assertThat(payload.getSenderNickName()).isEqualTo("신청자");
        assertThat(payload.getContent()).isEqualTo("안녕하세요, 나눔 가능할까요?");
        assertThat(payload.getCreatedAt()).isNotNull();

        ChatMessage saved = chatMessageRepository.findById(payload.getMessageId()).orElseThrow();
        assertThat(saved.getRoomId()).isEqualTo(roomId);
        assertThat(saved.getSenderId()).isEqualTo(requester);
        assertThat(saved.getContent()).isEqualTo("안녕하세요, 나눔 가능할까요?");
    }

    @Test
    @DisplayName("메시지를 보내면 수신자(발신자 제외)의 안읽음 수만 1 증가하고, 발신자의 안읽음 수는 그대로다.")
    void sendIncrementsRecipientUnreadCountOnly() {
        // given
        Long ownerId = saveMember("등록자");
        Long requester = saveMember("신청자");
        Long foodId = saveFood(ownerId, "미개봉 시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        ChattingMember ownerMember = saveChattingMember(roomId, ownerId, ChatRole.OWNER, 0L, 0L);
        ChattingMember requesterMember = saveChattingMember(roomId, requester, ChatRole.MEMBER, 0L, 0L);

        // when
        chatMessageService.send(requester, roomId, "안녕하세요");

        // then
        ChattingMember owner = chattingMemberRepository.findById(ownerMember.getChattingMemberId()).orElseThrow();
        ChattingMember me = chattingMemberRepository.findById(requesterMember.getChattingMemberId()).orElseThrow();
        assertThat(owner.getUnreadCount()).isEqualTo(1L);
        assertThat(me.getUnreadCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("payload와 함께 발신자를 제외한 수신자 memberId 목록을 반환한다.")
    void sendReturnsRecipientIdsExcludingSender() {
        // given
        Long ownerId = saveMember("등록자");
        Long requester = saveMember("신청자");
        Long foodId = saveFood(ownerId, "미개봉 시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveChattingMember(roomId, ownerId, ChatRole.OWNER, 0L);
        saveChattingMember(roomId, requester, ChatRole.MEMBER, 0L);

        // when
        ChatMessageService.SentMessage sent = chatMessageService.send(requester, roomId, "안녕하세요");

        // then
        assertThat(sent.recipientIds()).containsExactly(ownerId);
    }

    @Test
    @DisplayName("방 참여자가 아닌 회원이 메시지를 보내면 예외가 발생하고 메시지는 저장되지 않는다.")
    void sendByNonMember() {
        // given
        Long ownerId = saveMember("등록자");
        Long requester = saveMember("신청자");
        Long stranger = saveMember("외부인");
        Long foodId = saveFood(ownerId, "미개봉 시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveChattingMember(roomId, ownerId, ChatRole.OWNER, 0L);
        saveChattingMember(roomId, requester, ChatRole.MEMBER, 0L);

        // when // then
        assertThatThrownBy(() -> chatMessageService.send(stranger, roomId, "끼어들기"))
                .isInstanceOf(ForbiddenChatAccessException.class);
        assertThat(chatMessageRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 채팅방으로 메시지를 보내면 예외가 발생한다.")
    void sendToNonexistentRoom() {
        // given
        Long requester = saveMember("신청자");

        // when // then
        assertThatThrownBy(() -> chatMessageService.send(requester, 999_999L, "안녕하세요"))
                .isInstanceOf(ChatRoomNotFoundException.class);
    }

    private Long saveMember(String nickName) {
        return memberRepository.save(Member.builder()
                .nickName(nickName)
                .email(nickName + "@example.com")
                .password("password")
                .build()).getId();
    }

    private Long saveFood(Long ownerId, String foodName) {
        return foodRepository.save(Food.builder()
                .memberId(ownerId)
                .foodName(foodName)
                .details("상세 내용")
                .capacity(3)
                .exStatus(IN_PROGRESS)
                .expired(LocalDateTime.now().plusDays(7))
                .build()).getFoodId();
    }

    private Long saveRoom(Long foodId, Long ownerId) {
        return chatRoomRepository.save(ChatRoom.builder()
                .foodId(foodId)
                .ownerId(ownerId)
                .build()).getRoomId();
    }

    private ChattingMember saveChattingMember(Long roomId, Long memberId, ChatRole role, Long lastReadMessageId) {
        return saveChattingMember(roomId, memberId, role, lastReadMessageId, 0L);
    }

    private ChattingMember saveChattingMember(Long roomId, Long memberId, ChatRole role,
                                              Long lastReadMessageId, Long unreadCount) {
        return chattingMemberRepository.save(ChattingMember.builder()
                .roomId(roomId)
                .memberId(memberId)
                .role(role)
                .lastReadMessageId(lastReadMessageId)
                .unreadCount(unreadCount)
                .build());
    }
}
