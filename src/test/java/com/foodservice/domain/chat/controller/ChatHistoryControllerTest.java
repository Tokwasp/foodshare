package com.foodservice.domain.chat.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.exception.chat.ChatRoomNotFoundException;
import com.foodservice.common.exception.chat.ForbiddenChatAccessException;
import com.foodservice.domain.chat.dto.response.ChatHistoryResponse;
import com.foodservice.domain.chat.dto.response.ChatMessageResponse;
import com.foodservice.domain.chat.dto.response.ChatRoomListResponse;
import com.foodservice.domain.chat.service.ChatHistoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatHistoryController.class)
class ChatHistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatHistoryService chatHistoryService;

    @Test
    @DisplayName("내 채팅방 목록을 조회하면 200과 방 목록을 반환한다.")
    void getMyRooms() throws Exception {
        // given
        Long memberId = 2L;
        ChatRoomListResponse room = new ChatRoomListResponse(
                700L, 100L, "미개봉 시리얼", "상대닉네임",
                "안녕하세요, 나눔 가능할까요?", LocalDateTime.now(), 3);
        given(chatHistoryService.getMyRooms(eq(memberId)))
                .willReturn(List.of(room));

        // when // then
        mockMvc.perform(get("/api/v1/members/me/chat/rooms")
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].roomId").value(700))
                .andExpect(jsonPath("$.data[0].foodId").value(100))
                .andExpect(jsonPath("$.data[0].foodName").value("미개봉 시리얼"))
                .andExpect(jsonPath("$.data[0].partnerNickName").value("상대닉네임"))
                .andExpect(jsonPath("$.data[0].lastMessage").value("안녕하세요, 나눔 가능할까요?"))
                .andExpect(jsonPath("$.data[0].unreadCount").value(3))
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다."));
    }

    @Test
    @DisplayName("방 진입(direction 미지정)은 initial로 조회되어 메시지·앵커·양방향 커서를 반환한다.")
    void getMessagesInitial() throws Exception {
        // given
        Long memberId = 2L;
        ChatMessageResponse message = new ChatMessageResponse(
                9001L, 2L, "상대닉네임", "안녕하세요, 나눔 가능할까요?", false, LocalDateTime.now());
        given(chatHistoryService.getMessages(eq(memberId), eq(700L), eq("initial"), isNull(), eq(20)))
                .willReturn(new ChatHistoryResponse(List.of(message), 8990L, 9001L, 9020L, true, true));

        // when // then
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", 700L)
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages[0].messageId").value(9001))
                .andExpect(jsonPath("$.data.messages[0].senderNickName").value("상대닉네임"))
                .andExpect(jsonPath("$.data.messages[0].mine").value(false))
                .andExpect(jsonPath("$.data.anchorMessageId").value(8990))
                .andExpect(jsonPath("$.data.upCursor").value(9001))
                .andExpect(jsonPath("$.data.downCursor").value(9020))
                .andExpect(jsonPath("$.data.hasPrev").value(true))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다."));
    }

    @Test
    @DisplayName("direction·cursor·size를 전달하면 서비스에 그대로 전달된다.")
    void getMessagesWithDirectionCursorSize() throws Exception {
        // given
        Long memberId = 2L;
        given(chatHistoryService.getMessages(eq(memberId), eq(700L), eq("before"), eq(8980L), eq(30)))
                .willReturn(new ChatHistoryResponse(List.of(), null, null, null, false, false));

        // when // then
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", 700L)
                        .param("direction", "before")
                        .param("cursor", "8980")
                        .param("size", "30")
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPrev").value(false))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.upCursor").isEmpty())
                .andExpect(jsonPath("$.data.anchorMessageId").isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 채팅방의 이력을 조회하면 404와 CHAT_ROOM_NOT_FOUND를 반환한다.")
    void getMessagesChatRoomNotFound() throws Exception {
        // given
        Long memberId = 2L;
        willThrow(new ChatRoomNotFoundException())
                .given(chatHistoryService).getMessages(eq(memberId), eq(999L), eq("initial"), isNull(), eq(20));

        // when // then
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", 999L)
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("CHAT_ROOM_NOT_FOUND"));
    }

    @Test
    @DisplayName("방 참여자가 아닌 회원이 이력을 조회하면 403과 FORBIDDEN_CHAT_ACCESS를 반환한다.")
    void getMessagesForbidden() throws Exception {
        // given
        Long memberId = 2L;
        willThrow(new ForbiddenChatAccessException())
                .given(chatHistoryService).getMessages(eq(memberId), eq(700L), eq("initial"), isNull(), eq(20));

        // when // then
        mockMvc.perform(get("/api/v1/chat/rooms/{roomId}/messages", 700L)
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FORBIDDEN_CHAT_ACCESS"));
    }
}
