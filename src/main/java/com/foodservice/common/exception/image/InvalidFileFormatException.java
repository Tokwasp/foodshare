package com.foodservice.common.exception.image;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class InvalidFileFormatException extends BusinessException {
    public InvalidFileFormatException() {
        super(ErrorCode.INVALID_FILE_FORMAT);
    }
}
