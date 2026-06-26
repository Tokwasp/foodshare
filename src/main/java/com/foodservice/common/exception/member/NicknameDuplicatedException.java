package com.foodservice.common.exception.member;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class NicknameDuplicatedException extends BusinessException {

    public NicknameDuplicatedException() {
        super(ErrorCode.NICKNAME_DUPLICATED);
    }
}
