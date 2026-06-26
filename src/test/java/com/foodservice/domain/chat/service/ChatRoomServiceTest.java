package com.foodservice.domain.chat.service;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.exception.chat.SelfChatNotAllowedException;
import com.foodservice.common.exception.food.FoodNotAvailableException;
import com.foodservice.common.exception.food.FoodNotFoundException;
import com.foodservice.domain.chat.dto.response.ChatRoomCreateResponse;
import com.foodservice.domain.chat.entity.ChatRole;
import com.foodservice.domain.chat.entity.ChattingMember;
import com.foodservice.domain.chat.repository.ChatRoomRepository;
import com.foodservice.domain.chat.repository.ChattingMemberRepository;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

import static com.foodservice.domain.food.entity.ExStatus.COMPLETED;
import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class ChatRoomServiceTest extends IntegrationTestSupport {

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private ChattingMemberRepository chattingMemberRepository;

    @Autowired
    private FoodRepository foodRepository;

    @Test
    @DisplayName("새 채팅방을 만들면 방과 등록자(OWNER)/요청자(MEMBER) ChattingMember 2행이 생성되고 created=true를 반환한다.")
    void createOrGetRoomNew() {
        // given
        Long ownerId = 1L;
        Long requesterId = 2L;
        Long foodId = saveFood(ownerId, IN_PROGRESS);

        // when
        ChatRoomCreateResponse response = chatRoomService.createOrGetRoom(requesterId, foodId);

        // then
        assertThat(response.isCreated()).isTrue();
        assertThat(response.getFoodId()).isEqualTo(foodId);

        List<ChattingMember> members = chattingMemberRepository.findByRoomId(response.getRoomId());
        assertThat(members).hasSize(2)
                .extracting("memberId", "role")
                .containsExactlyInAnyOrder(
                        tuple(ownerId, ChatRole.OWNER),
                        tuple(requesterId, ChatRole.MEMBER)
                );
    }

    @Test
    @DisplayName("같은 물품에 같은 회원이 다시 요청하면 기존 방을 반환하고(created=false) 중복 생성하지 않는다.")
    void createOrGetRoomReturnsExisting() {
        // given
        Long ownerId = 1L;
        Long requesterId = 2L;
        Long foodId = saveFood(ownerId, IN_PROGRESS);
        ChatRoomCreateResponse first = chatRoomService.createOrGetRoom(requesterId, foodId);

        // when
        ChatRoomCreateResponse second = chatRoomService.createOrGetRoom(requesterId, foodId);

        // then
        assertThat(second.isCreated()).isFalse();
        assertThat(second.getRoomId()).isEqualTo(first.getRoomId());
        assertThat(chatRoomRepository.findAll()).hasSize(1);
        assertThat(chattingMemberRepository.findByRoomId(first.getRoomId())).hasSize(2);
    }

    @Test
    @DisplayName("본인 물품에는 채팅방을 만들 수 없다.")
    void createOrGetRoomSelfChat() {
        // given
        Long ownerId = 1L;
        Long foodId = saveFood(ownerId, IN_PROGRESS);

        // when // then
        assertThatThrownBy(() -> chatRoomService.createOrGetRoom(ownerId, foodId))
                .isInstanceOf(SelfChatNotAllowedException.class);
    }

    @Test
    @DisplayName("존재하지 않는 물품이면 예외가 발생한다.")
    void createOrGetRoomFoodNotFound() {
        // when // then
        assertThatThrownBy(() -> chatRoomService.createOrGetRoom(2L, 999_999L))
                .isInstanceOf(FoodNotFoundException.class);
    }

    @Test
    @DisplayName("IN_PROGRESS가 아닌 물품에는 새 채팅방을 만들 수 없다.")
    void createOrGetRoomFoodNotAvailable() {
        // given
        Long ownerId = 1L;
        Long foodId = saveFood(ownerId, COMPLETED);

        // when // then
        assertThatThrownBy(() -> chatRoomService.createOrGetRoom(2L, foodId))
                .isInstanceOf(FoodNotAvailableException.class);
    }

    private Long saveFood(Long ownerId, ExStatus exStatus) {
        return foodRepository.save(Food.builder()
                .memberId(ownerId)
                .foodName("미개봉 시리얼")
                .details("상세 내용")
                .capacity(3)
                .exStatus(exStatus)
                .expired(LocalDateTime.now().plusDays(7))
                .build()).getFoodId();
    }
}
