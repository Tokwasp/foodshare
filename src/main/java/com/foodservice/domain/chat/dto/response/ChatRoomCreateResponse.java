package com.foodservice.domain.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class ChatRoomCreateResponse {

    private Long roomId;
    private Long foodId;
    private boolean created;

    public static ChatRoomCreateResponse of(Long roomId, Long foodId, boolean created) {
        return new ChatRoomCreateResponse(roomId, foodId, created);
    }
}
