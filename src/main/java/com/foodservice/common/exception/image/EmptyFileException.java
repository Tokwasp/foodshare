package com.foodservice.common.exception.image;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class EmptyFileException extends BusinessException {
    public EmptyFileException() {
        super(ErrorCode.EMPTY_FILE);
    }
}
