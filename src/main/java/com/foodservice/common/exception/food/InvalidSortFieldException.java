package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class InvalidSortFieldException extends BusinessException {

    public InvalidSortFieldException() {
        super(ErrorCode.INVALID_SORT_FIELD);
    }
}
