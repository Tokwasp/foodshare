package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class CapacityTooSmallException extends BusinessException {

    public CapacityTooSmallException() {
        super(ErrorCode.CAPACITY_TOO_SMALL);
    }
}
