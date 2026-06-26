package com.foodservice.domain.chat.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.response.ApiResponse;
import com.foodservice.domain.chat.dto.response.ChatHistoryResponse;
import com.foodservice.domain.chat.dto.response.ChatRoomListResponse;
import com.foodservice.domain.chat.service.ChatHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    @GetMapping("/members/me/chat/rooms")
    public ResponseEntity<ApiResponse<List<ChatRoomListResponse>>> getMyRooms(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId
    ) {
        List<ChatRoomListResponse> rooms = chatHistoryService.getMyRooms(memberId);
        return ResponseEntity.ok(ApiResponse.success(rooms, "조회에 성공했습니다."));
    }

    @GetMapping("/chat/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatHistoryResponse>> getMessages(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "initial") String direction,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        ChatHistoryResponse response = chatHistoryService.getMessages(memberId, roomId, direction, cursor, size);
        return ResponseEntity.ok(ApiResponse.success(response, "조회에 성공했습니다."));
    }
}
