package com.foodservice.common.exception.member;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class MemberNotFoundException extends BusinessException {

    public MemberNotFoundException() {
        super(ErrorCode.MEMBER_NOT_FOUND);
    }
}
