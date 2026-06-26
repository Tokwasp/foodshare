package com.foodservice.domain.chat.repository;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.domain.chat.entity.ChatMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class ChatMessageRepositoryTest extends IntegrationTestSupport {

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Test
    @DisplayName("cursor 없이 조회하면 해당 방의 메시지를 messageId 내림차순으로 limit만큼 가져온다.")
    void findLatestMessages() {
        // given
        Long roomId = 700L;
        saveMessage(roomId, 1L, "m1");
        saveMessage(roomId, 2L, "m2");
        saveMessage(roomId, 1L, "m3");
        saveMessage(999L, 1L, "다른 방 메시지");

        // when
        List<ChatMessage> result = chatMessageRepository.findLatestMessages(roomId, Limit.of(2));

        // then
        assertThat(result).hasSize(2)
                .extracting("content")
                .containsExactly("m3", "m2");
    }

    @Test
    @DisplayName("최근 메시지 1건만 조회하면 방의 가장 마지막 메시지를 반환한다.")
    void findLatestMessagesLimitOne() {
        // given
        Long roomId = 700L;
        saveMessage(roomId, 1L, "예전 메시지");
        saveMessage(roomId, 2L, "최근 메시지");

        // when
        List<ChatMessage> result = chatMessageRepository.findLatestMessages(roomId, Limit.of(1));

        // then
        assertThat(result).hasSize(1)
                .extracting("content")
                .containsExactly("최근 메시지");
    }

    @Test
    @DisplayName("메시지가 없는 방의 최근 메시지를 조회하면 빈 결과를 반환한다.")
    void findLatestMessagesWhenEmpty() {
        // when
        List<ChatMessage> result = chatMessageRepository.findLatestMessages(404L, Limit.of(1));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("cursor를 전달하면 messageId가 cursor보다 작은 과거 메시지를 내림차순으로 가져온다.")
    void findMessagesBefore() {
        // given
        Long roomId = 700L;
        saveMessage(roomId, 1L, "m1");
        saveMessage(roomId, 2L, "m2");
        ChatMessage m3 = saveMessage(roomId, 1L, "m3");

        // when
        List<ChatMessage> result =
                chatMessageRepository.findMessagesBefore(roomId, m3.getMessageId(), Limit.of(10));

        // then
        assertThat(result).extracting("content")
                .containsExactly("m2", "m1");
    }

    @Test
    @DisplayName("cursor를 전달하면 messageId가 cursor보다 큰 최신 메시지를 오름차순으로 가져온다.")
    void findMessagesAfter() {
        // given
        Long roomId = 700L;
        ChatMessage m1 = saveMessage(roomId, 1L, "m1");
        saveMessage(roomId, 2L, "m2");
        saveMessage(roomId, 1L, "m3");
        saveMessage(999L, 1L, "다른 방 메시지");

        // when
        List<ChatMessage> result =
                chatMessageRepository.findMessagesAfter(roomId, m1.getMessageId(), Limit.of(10));

        // then
        assertThat(result).extracting("content")
                .containsExactly("m2", "m3");
    }

    @Test
    @DisplayName("findMessagesAfter는 limit 만큼만 오래된 순으로 가져온다.")
    void findMessagesAfterRespectsLimit() {
        // given
        Long roomId = 700L;
        ChatMessage m1 = saveMessage(roomId, 1L, "m1");
        saveMessage(roomId, 2L, "m2");
        saveMessage(roomId, 1L, "m3");
        saveMessage(roomId, 1L, "m4");

        // when
        List<ChatMessage> result =
                chatMessageRepository.findMessagesAfter(roomId, m1.getMessageId(), Limit.of(2));

        // then
        assertThat(result).extracting("content")
                .containsExactly("m2", "m3");
    }

    @Test
    @DisplayName("anchor 이하(포함) 메시지를 최신순으로 limit 만큼 가져온다. (방 진입 위쪽 묶음)")
    void findMessagesAtOrBefore() {
        // given
        Long roomId = 700L;
        saveMessage(roomId, 1L, "m1");
        saveMessage(roomId, 2L, "m2");
        ChatMessage m3 = saveMessage(roomId, 1L, "m3");
        saveMessage(roomId, 1L, "m4");

        // when (anchor = m3 → m3 포함 그 이하 최신순)
        List<ChatMessage> result =
                chatMessageRepository.findMessagesAtOrBefore(roomId, m3.getMessageId(), Limit.of(10));

        // then
        assertThat(result).extracting("content")
                .containsExactly("m3", "m2", "m1");
    }

    @Test
    @DisplayName("findMessagesAtOrBefore는 limit 만큼만 최신순으로 가져온다.")
    void findMessagesAtOrBeforeRespectsLimit() {
        // given
        Long roomId = 700L;
        saveMessage(roomId, 1L, "m1");
        saveMessage(roomId, 2L, "m2");
        ChatMessage m3 = saveMessage(roomId, 1L, "m3");

        // when
        List<ChatMessage> result =
                chatMessageRepository.findMessagesAtOrBefore(roomId, m3.getMessageId(), Limit.of(2));

        // then
        assertThat(result).extracting("content")
                .containsExactly("m3", "m2");
    }

    @Test
    @DisplayName("방 안에서 특정 messageId보다 과거/최신 메시지의 존재 여부를 확인한다.")
    void existsAroundMessageId() {
        // given
        Long roomId = 700L;
        saveMessage(roomId, 1L, "m1");
        ChatMessage m2 = saveMessage(roomId, 2L, "m2");
        saveMessage(roomId, 1L, "m3");

        // when // then
        assertThat(chatMessageRepository.existsByRoomIdAndMessageIdLessThan(roomId, m2.getMessageId())).isTrue();
        assertThat(chatMessageRepository.existsByRoomIdAndMessageIdGreaterThan(roomId, m2.getMessageId())).isTrue();

        ChatMessage oldest = chatMessageRepository.findLatestMessages(roomId, Limit.of(3)).get(2);
        assertThat(chatMessageRepository.existsByRoomIdAndMessageIdLessThan(roomId, oldest.getMessageId())).isFalse();
    }

    @Test
    @DisplayName("여러 방의 마지막 메시지를 한 번에 방별로 조회한다.")
    void findLastMessagesByRoomIds() {
        // given
        saveMessage(700L, 1L, "700-이전");
        saveMessage(700L, 2L, "700-마지막");
        saveMessage(701L, 1L, "701-마지막");
        saveMessage(999L, 1L, "조회 안 할 방");

        // when
        List<ChatMessage> result = chatMessageRepository.findLastMessagesByRoomIds(List.of(700L, 701L));

        // then
        assertThat(result).hasSize(2)
                .extracting("roomId", "content")
                .containsExactlyInAnyOrder(
                        tuple(700L, "700-마지막"),
                        tuple(701L, "701-마지막")
                );
    }

    @Test
    @DisplayName("메시지가 없는 방은 마지막 메시지 일괄 조회 결과에 포함되지 않는다.")
    void findLastMessagesByRoomIdsExcludesEmptyRoom() {
        // given
        saveMessage(700L, 1L, "700-마지막");

        // when
        List<ChatMessage> result = chatMessageRepository.findLastMessagesByRoomIds(List.of(700L, 701L));

        // then
        assertThat(result).hasSize(1)
                .extracting("roomId")
                .containsExactly(700L);
    }

    private ChatMessage saveMessage(Long roomId, Long senderId, String content) {
        return chatMessageRepository.save(ChatMessage.builder()
                .roomId(roomId)
                .senderId(senderId)
                .content(content)
                .build());
    }
}
