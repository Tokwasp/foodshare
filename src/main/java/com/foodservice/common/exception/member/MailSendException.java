package com.foodservice.common.exception.member;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class MailSendException extends BusinessException {

    public MailSendException(Throwable cause) {
        super(ErrorCode.EMAIL_SEND_FAILED, cause);
    }
}
