package com.foodservice.common.exception.foodrequest;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class InvalidRequestStatusException extends BusinessException {

    public InvalidRequestStatusException() {
        super(ErrorCode.INVALID_REQUEST_STATUS);
    }
}
