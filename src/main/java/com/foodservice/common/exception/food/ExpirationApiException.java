package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class ExpirationApiException extends BusinessException {

    public ExpirationApiException() {
        super(ErrorCode.EXPIRATION_API_ERROR);
    }

    public ExpirationApiException(Throwable cause) {
        super(ErrorCode.EXPIRATION_API_ERROR, cause);
    }
}
