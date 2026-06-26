package com.foodservice.domain.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class ChatHistoryResponse {

    private List<ChatMessageResponse> messages;
    private Long anchorMessageId;
    private Long upCursor;
    private Long downCursor;
    private boolean hasPrev;
    private boolean hasNext;

    public static ChatHistoryResponse of(List<ChatMessageResponse> messages, Long anchorMessageId,
                                         Long upCursor, Long downCursor, boolean hasPrev, boolean hasNext) {
        return new ChatHistoryResponse(messages, anchorMessageId, upCursor, downCursor, hasPrev, hasNext);
    }
}
