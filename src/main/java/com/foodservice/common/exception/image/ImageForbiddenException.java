package com.foodservice.common.exception.image;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class ImageForbiddenException extends BusinessException {

    public ImageForbiddenException() {
        super(ErrorCode.FORBIDDEN_IMAGE_ACCESS);
    }
}
