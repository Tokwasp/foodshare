package com.foodservice.common.exception.chat;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class ForbiddenChatAccessException extends BusinessException {

    public ForbiddenChatAccessException() {
        super(ErrorCode.FORBIDDEN_CHAT_ACCESS);
    }
}
