package com.foodservice.common.exception.member;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class CodeExpiredException extends BusinessException {

    public CodeExpiredException() {
        super(ErrorCode.CODE_EXPIRED);
    }
}
