package com.foodservice.common.exception.member;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class CodeMismatchException extends BusinessException {

    public CodeMismatchException() {
        super(ErrorCode.CODE_MISMATCH);
    }
}
