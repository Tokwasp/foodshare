package com.foodservice.common.exception.chat;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class SelfChatNotAllowedException extends BusinessException {

    public SelfChatNotAllowedException() {
        super(ErrorCode.SELF_CHAT_NOT_ALLOWED);
    }
}
