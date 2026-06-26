package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class ExpiredImageRequiredException extends BusinessException {
    public ExpiredImageRequiredException() {
        super(ErrorCode.EXPIRED_IMAGE_REQUIRED);
    }
}
