package com.foodservice.common.exception.image;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class ExpiredImageNotFoundException extends BusinessException {

    public ExpiredImageNotFoundException() {
        super(ErrorCode.EXPIRED_IMAGE_NOT_FOUND);
    }
}
