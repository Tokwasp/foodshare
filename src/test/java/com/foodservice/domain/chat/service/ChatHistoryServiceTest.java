package com.foodservice.domain.chat.service;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.exception.chat.ChatRoomNotFoundException;
import com.foodservice.common.exception.chat.ForbiddenChatAccessException;
import com.foodservice.domain.chat.dto.response.ChatHistoryResponse;
import com.foodservice.domain.chat.dto.response.ChatRoomListResponse;
import com.foodservice.domain.chat.entity.ChatMessage;
import com.foodservice.domain.chat.entity.ChatRole;
import com.foodservice.domain.chat.entity.ChatRoom;
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
import java.util.List;

import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ChatHistoryServiceTest extends IntegrationTestSupport {

    @Autowired
    private ChatHistoryService chatHistoryService;

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
    @DisplayName("내 채팅방 목록은 상대 닉네임·물품명·마지막 메시지·안읽음 수(비정규화)를 담아 반환한다.")
    void getMyRooms() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "미개봉 시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);
        saveMember(roomId, me, ChatRole.MEMBER, 0L, 2L); // 비정규화된 안읽음 수 2

        saveMessage(roomId, ownerId, "안녕하세요, 나눔 가능할까요?");
        saveMessage(roomId, ownerId, "지금도 남아있어요");

        // when
        List<ChatRoomListResponse> rooms = chatHistoryService.getMyRooms(me);

        // then
        assertThat(rooms).hasSize(1);
        ChatRoomListResponse room = rooms.get(0);
        assertThat(room.getRoomId()).isEqualTo(roomId);
        assertThat(room.getFoodId()).isEqualTo(foodId);
        assertThat(room.getFoodName()).isEqualTo("미개봉 시리얼");
        assertThat(room.getPartnerNickName()).isEqualTo("등록자");
        assertThat(room.getLastMessage()).isEqualTo("지금도 남아있어요");
        assertThat(room.getUnreadCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("내 채팅방 목록은 마지막 메시지 시각 내림차순으로 정렬된다.")
    void getMyRoomsOrderedByLastMessage() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");

        Long foodA = saveFood(ownerId, "물품A");
        Long roomA = saveRoom(foodA, ownerId);
        saveMember(roomA, ownerId, ChatRole.OWNER, 0L);
        saveMember(roomA, me, ChatRole.MEMBER, 0L);
        saveMessage(roomA, ownerId, "A 먼저");

        Long foodB = saveFood(ownerId, "물품B");
        Long roomB = saveRoom(foodB, ownerId);
        saveMember(roomB, ownerId, ChatRole.OWNER, 0L);
        saveMember(roomB, me, ChatRole.MEMBER, 0L);
        saveMessage(roomB, ownerId, "B 나중");

        // when
        List<ChatRoomListResponse> rooms = chatHistoryService.getMyRooms(me);

        // then
        assertThat(rooms).extracting("roomId")
                .containsExactly(roomB, roomA);
    }

    @Test
    @DisplayName("방 진입(initial)은 마지막 읽은 메시지 기준 위(포함)·아래를 함께 내림차순으로 반환하고 mine·앵커를 채운다.")
    void getMessagesInitialCentered() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);

        ChatMessage m1 = saveMessage(roomId, ownerId, "안녕하세요");
        ChatMessage m2 = saveMessage(roomId, me, "네 안녕하세요");
        ChatMessage m3 = saveMessage(roomId, ownerId, "남아있어요");
        ChatMessage m4 = saveMessage(roomId, me, "받고 싶어요");
        ChatMessage m5 = saveMessage(roomId, ownerId, "가능합니다");
        saveMember(roomId, me, ChatRole.MEMBER, m3.getMessageId(), 2L); // m3까지 읽음

        // when
        ChatHistoryResponse response = chatHistoryService.getMessages(me, roomId, "initial", null, 20);

        // then — 위(<= m3): m3,m2,m1 + 아래(> m3): m4,m5 → 내림차순 병합
        assertThat(response.getMessages())
                .extracting("content", "mine", "senderNickName")
                .containsExactly(
                        tuple("가능합니다", false, "등록자"),
                        tuple("받고 싶어요", true, "나"),
                        tuple("남아있어요", false, "등록자"),
                        tuple("네 안녕하세요", true, "나"),
                        tuple("안녕하세요", false, "등록자")
                );
        assertThat(response.getAnchorMessageId()).isEqualTo(m3.getMessageId());
        assertThat(response.getUpCursor()).isEqualTo(m1.getMessageId());
        assertThat(response.getDownCursor()).isEqualTo(m5.getMessageId());
        assertThat(response.isHasPrev()).isFalse();
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    @DisplayName("신규 입장(lastRead=0)은 위쪽이 비어 처음부터 최신까지 내림차순으로 반환한다.")
    void getMessagesInitialNewMember() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);
        saveMember(roomId, me, ChatRole.MEMBER, 0L);

        ChatMessage m1 = saveMessage(roomId, ownerId, "m1");
        saveMessage(roomId, ownerId, "m2");
        ChatMessage m3 = saveMessage(roomId, ownerId, "m3");

        // when
        ChatHistoryResponse response = chatHistoryService.getMessages(me, roomId, "initial", null, 20);

        // then
        assertThat(response.getMessages()).extracting("content")
                .containsExactly("m3", "m2", "m1");
        assertThat(response.getAnchorMessageId()).isEqualTo(0L);
        assertThat(response.getUpCursor()).isEqualTo(m1.getMessageId());
        assertThat(response.getDownCursor()).isEqualTo(m3.getMessageId());
        assertThat(response.isHasPrev()).isFalse();
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    @DisplayName("이미 다 읽고 재입장(lastRead=최신)하면 아래쪽이 비어 최신 묶음만 반환한다.")
    void getMessagesInitialAllRead() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);

        ChatMessage m1 = saveMessage(roomId, ownerId, "m1");
        saveMessage(roomId, ownerId, "m2");
        ChatMessage m3 = saveMessage(roomId, ownerId, "m3");
        saveMember(roomId, me, ChatRole.MEMBER, m3.getMessageId(), 0L); // 다 읽음

        // when
        ChatHistoryResponse response = chatHistoryService.getMessages(me, roomId, "initial", null, 20);

        // then
        assertThat(response.getMessages()).extracting("content")
                .containsExactly("m3", "m2", "m1");
        assertThat(response.getAnchorMessageId()).isEqualTo(m3.getMessageId());
        assertThat(response.getUpCursor()).isEqualTo(m1.getMessageId());
        assertThat(response.getDownCursor()).isEqualTo(m3.getMessageId());
        assertThat(response.isHasPrev()).isFalse();
        assertThat(response.isHasNext()).isFalse();
    }

    @Test
    @DisplayName("방 진입 시 size를 넘어 양끝에 메시지가 더 있으면 hasPrev/hasNext가 true다.")
    void getMessagesInitialHasBothSides() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);

        saveMessage(roomId, ownerId, "m1");
        ChatMessage m2 = saveMessage(roomId, ownerId, "m2");
        ChatMessage m3 = saveMessage(roomId, ownerId, "m3");
        ChatMessage m4 = saveMessage(roomId, ownerId, "m4");
        saveMessage(roomId, ownerId, "m5");
        saveMember(roomId, me, ChatRole.MEMBER, m3.getMessageId(), 2L); // m3까지 읽음

        // when — 각 방향 1개씩만 (위: m3, 아래: m4)
        ChatHistoryResponse response = chatHistoryService.getMessages(me, roomId, "initial", null, 1);

        // then
        assertThat(response.getMessages()).extracting("content")
                .containsExactly("m4", "m3");
        assertThat(response.getUpCursor()).isEqualTo(m3.getMessageId());
        assertThat(response.getDownCursor()).isEqualTo(m4.getMessageId());
        assertThat(response.isHasPrev()).isTrue();  // m2,m1 더 있음
        assertThat(response.isHasNext()).isTrue();  // m5 더 있음
    }

    @Test
    @DisplayName("방 진입은 화면에 안 띄운 최신이 남아도 lastReadMessageId를 방의 최신으로 올리고 안읽음을 0으로 만든다.")
    void getMessagesInitialMarksAllRead() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);

        saveMessage(roomId, ownerId, "m1");
        ChatMessage m2 = saveMessage(roomId, ownerId, "m2");
        saveMessage(roomId, ownerId, "m3");
        saveMessage(roomId, ownerId, "m4");
        ChatMessage m5 = saveMessage(roomId, ownerId, "m5");
        ChattingMember myMembership = saveMember(roomId, me, ChatRole.MEMBER, m2.getMessageId(), 3L);

        // when — size 1이라 화면엔 일부만 오지만, 읽음은 방 최신(m5)까지로 처리
        chatHistoryService.getMessages(me, roomId, "initial", null, 1);

        // then
        ChattingMember updated = chattingMemberRepository.findById(myMembership.getChattingMemberId()).orElseThrow();
        assertThat(updated.getLastReadMessageId()).isEqualTo(m5.getMessageId());
        assertThat(updated.getUnreadCount()).isEqualTo(0L);
    }

    @Test
    @DisplayName("위로 스크롤(before)은 cursor보다 과거 메시지를 내림차순으로 가져오고 읽음 상태를 바꾸지 않는다.")
    void getMessagesBefore() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);
        ChattingMember myMembership = saveMember(roomId, me, ChatRole.MEMBER, 0L, 3L);

        ChatMessage m1 = saveMessage(roomId, ownerId, "m1");
        ChatMessage m2 = saveMessage(roomId, ownerId, "m2");
        ChatMessage m3 = saveMessage(roomId, ownerId, "m3");
        saveMessage(roomId, ownerId, "m4");

        // when
        ChatHistoryResponse response =
                chatHistoryService.getMessages(me, roomId, "before", m3.getMessageId(), 20);

        // then
        assertThat(response.getMessages()).extracting("content")
                .containsExactly("m2", "m1");
        assertThat(response.getAnchorMessageId()).isNull();
        assertThat(response.getUpCursor()).isEqualTo(m1.getMessageId());
        assertThat(response.getDownCursor()).isEqualTo(m2.getMessageId());
        assertThat(response.isHasPrev()).isFalse();
        assertThat(response.isHasNext()).isTrue(); // m3,m4 더 있음

        ChattingMember updated = chattingMemberRepository.findById(myMembership.getChattingMemberId()).orElseThrow();
        assertThat(updated.getLastReadMessageId()).isEqualTo(0L);
        assertThat(updated.getUnreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("아래로 스크롤(after)은 cursor보다 최신 메시지를 내림차순으로 가져오고 읽음 상태를 바꾸지 않는다.")
    void getMessagesAfter() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);
        ChattingMember myMembership = saveMember(roomId, me, ChatRole.MEMBER, 0L, 3L);

        saveMessage(roomId, ownerId, "m1");
        ChatMessage m2 = saveMessage(roomId, ownerId, "m2");
        ChatMessage m3 = saveMessage(roomId, ownerId, "m3");
        ChatMessage m4 = saveMessage(roomId, ownerId, "m4");

        // when
        ChatHistoryResponse response =
                chatHistoryService.getMessages(me, roomId, "after", m2.getMessageId(), 20);

        // then
        assertThat(response.getMessages()).extracting("content")
                .containsExactly("m4", "m3");
        assertThat(response.getAnchorMessageId()).isNull();
        assertThat(response.getUpCursor()).isEqualTo(m3.getMessageId());
        assertThat(response.getDownCursor()).isEqualTo(m4.getMessageId());
        assertThat(response.isHasPrev()).isTrue(); // m2,m1 더 있음
        assertThat(response.isHasNext()).isFalse();

        ChattingMember updated = chattingMemberRepository.findById(myMembership.getChattingMemberId()).orElseThrow();
        assertThat(updated.getLastReadMessageId()).isEqualTo(0L);
        assertThat(updated.getUnreadCount()).isEqualTo(3L);
    }

    @Test
    @DisplayName("존재하지 않는 채팅방의 이력을 조회하면 예외가 발생한다.")
    void getMessagesChatRoomNotFound() {
        // when // then
        assertThatThrownBy(() -> chatHistoryService.getMessages(2L, 999_999L, "initial", null, 20))
                .isInstanceOf(ChatRoomNotFoundException.class);
    }

    @Test
    @DisplayName("방 참여자가 아닌 회원이 이력을 조회하면 예외가 발생한다.")
    void getMessagesForbidden() {
        // given
        Long ownerId = saveMember("등록자");
        Long me = saveMember("나");
        Long stranger = saveMember("외부인");
        Long foodId = saveFood(ownerId, "시리얼");
        Long roomId = saveRoom(foodId, ownerId);
        saveMember(roomId, ownerId, ChatRole.OWNER, 0L);
        saveMember(roomId, me, ChatRole.MEMBER, 0L);

        // when // then
        assertThatThrownBy(() -> chatHistoryService.getMessages(stranger, roomId, "initial", null, 20))
                .isInstanceOf(ForbiddenChatAccessException.class);
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

    private ChattingMember saveMember(Long roomId, Long memberId, ChatRole role, Long lastReadMessageId) {
        return saveMember(roomId, memberId, role, lastReadMessageId, 0L);
    }

    private ChattingMember saveMember(Long roomId, Long memberId, ChatRole role,
                                      Long lastReadMessageId, Long unreadCount) {
        return chattingMemberRepository.save(ChattingMember.builder()
                .roomId(roomId)
                .memberId(memberId)
                .role(role)
                .lastReadMessageId(lastReadMessageId)
                .unreadCount(unreadCount)
                .build());
    }

    private ChatMessage saveMessage(Long roomId, Long senderId, String content) {
        return chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content)
                .build());
    }
}
