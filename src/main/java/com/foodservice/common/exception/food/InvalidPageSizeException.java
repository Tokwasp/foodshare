package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class InvalidPageSizeException extends BusinessException {

    public InvalidPageSizeException() {
        super(ErrorCode.INVALID_PAGE_SIZE);
    }
}
