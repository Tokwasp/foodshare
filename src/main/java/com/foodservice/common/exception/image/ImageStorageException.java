package com.foodservice.common.exception.image;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class ImageStorageException extends BusinessException {
    public ImageStorageException() {
        super(ErrorCode.IMAGE_STORAGE_ERROR);
    }

    public ImageStorageException(Throwable cause) {
        super(ErrorCode.IMAGE_STORAGE_ERROR, cause);
    }
}
