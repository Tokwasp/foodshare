package com.foodservice.domain.chat.repository;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.domain.chat.entity.ChatRole;
import com.foodservice.domain.chat.entity.ChattingMember;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChattingMemberRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ChattingMemberRepository chattingMemberRepository;

    @Test
    @DisplayName("같은 방에 같은 회원을 두 번 저장하면 unique(roomId, memberId) 제약 위반으로 예외가 발생한다.")
    void uniqueRoomIdAndMemberId() {
        // given
        Long roomId = 700L;
        Long memberId = 2L;
        chattingMemberRepository.saveAndFlush(member(roomId, memberId));

        // when // then
        assertThatThrownBy(() -> chattingMemberRepository.saveAndFlush(member(roomId, memberId)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("회원이 참여한 모든 채팅방 멤버십을 조회한다.")
    void findByMemberId() {
        // given
        Long memberId = 2L;
        chattingMemberRepository.save(member(700L, memberId));
        chattingMemberRepository.save(member(701L, memberId));
        chattingMemberRepository.save(member(702L, 999L));

        // when
        List<ChattingMember> result = chattingMemberRepository.findByMemberId(memberId);

        // then
        assertThat(result).hasSize(2)
                .extracting("roomId")
                .containsExactlyInAnyOrder(700L, 701L);
    }

    @Test
    @DisplayName("여러 방 ID로 멤버십을 한 번에 조회한다.")
    void findByRoomIdIn() {
        // given
        chattingMemberRepository.save(member(700L, 1L));
        chattingMemberRepository.save(member(700L, 2L));
        chattingMemberRepository.save(member(701L, 3L));
        chattingMemberRepository.save(member(702L, 4L));

        // when
        List<ChattingMember> result = chattingMemberRepository.findByRoomIdIn(List.of(700L, 701L));

        // then
        assertThat(result).hasSize(3)
                .extracting("roomId")
                .containsExactlyInAnyOrder(700L, 700L, 701L);
    }

    @Test
    @DisplayName("방 ID와 회원 ID로 멤버십(참여 여부)을 조회한다.")
    void findByRoomIdAndMemberId() {
        // given
        Long roomId = 700L;
        Long memberId = 2L;
        chattingMemberRepository.save(member(roomId, memberId));

        // when
        Optional<ChattingMember> found = chattingMemberRepository.findByRoomIdAndMemberId(roomId, memberId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("참여하지 않은 회원으로 멤버십을 조회하면 빈 결과를 반환한다.")
    void findByRoomIdAndMemberIdWhenNotParticipant() {
        // given
        Long roomId = 700L;
        chattingMemberRepository.save(member(roomId, 2L));

        // when
        Optional<ChattingMember> found = chattingMemberRepository.findByRoomIdAndMemberId(roomId, 999L);

        // then
        assertThat(found).isEmpty();
    }

    private ChattingMember member(Long roomId, Long memberId) {
        return ChattingMember.builder()
                .roomId(roomId)
                .memberId(memberId)
                .role(ChatRole.MEMBER)
                .lastReadMessageId(0L)
                .build();
    }
}
