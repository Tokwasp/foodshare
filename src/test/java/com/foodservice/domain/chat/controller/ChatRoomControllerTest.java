package com.foodservice.domain.chat.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.exception.chat.SelfChatNotAllowedException;
import com.foodservice.domain.chat.dto.request.ChatRoomCreateRequest;
import com.foodservice.domain.chat.dto.response.ChatRoomCreateResponse;
import com.foodservice.domain.chat.service.ChatRoomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatRoomController.class)
class ChatRoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ChatRoomService chatRoomService;

    @Test
    @DisplayName("채팅방을 새로 생성하면 201과 created=true를 반환한다.")
    void createRoomNew() throws Exception {
        // given
        Long memberId = 2L;
        ChatRoomCreateRequest request = new ChatRoomCreateRequest(100L);
        given(chatRoomService.createOrGetRoom(eq(memberId), eq(100L)))
                .willReturn(ChatRoomCreateResponse.of(700L, 100L, true));

        // when // then
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomId").value(700))
                .andExpect(jsonPath("$.data.foodId").value(100))
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.message").value("채팅방이 생성되었습니다."));
    }

    @Test
    @DisplayName("이미 존재하는 채팅방을 요청하면 200과 created=false를 반환한다.")
    void createRoomExisting() throws Exception {
        // given
        Long memberId = 2L;
        ChatRoomCreateRequest request = new ChatRoomCreateRequest(100L);
        given(chatRoomService.createOrGetRoom(eq(memberId), eq(100L)))
                .willReturn(ChatRoomCreateResponse.of(700L, 100L, false));

        // when // then
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false));
    }

    @Test
    @DisplayName("foodId가 없으면 400과 VALIDATION_FAILED를 반환한다.")
    void createRoomWithoutFoodId() throws Exception {
        // given
        Long memberId = 2L;
        ChatRoomCreateRequest request = new ChatRoomCreateRequest(null);

        // when // then
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("본인 물품에 채팅방을 만들면 400과 SELF_CHAT_NOT_ALLOWED ProblemDetail을 반환한다.")
    void createRoomSelfChat() throws Exception {
        // given
        Long memberId = 1L;
        ChatRoomCreateRequest request = new ChatRoomCreateRequest(100L);
        willThrow(new SelfChatNotAllowedException())
                .given(chatRoomService).createOrGetRoom(eq(memberId), eq(100L));

        // when // then
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .sessionAttr(SessionConst.LOGIN_MEMBER_ID, memberId)
                        .content(objectMapper.writeValueAsString(request))
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("SELF_CHAT_NOT_ALLOWED"));
    }
}
