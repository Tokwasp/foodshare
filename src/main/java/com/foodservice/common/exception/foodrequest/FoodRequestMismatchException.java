package com.foodservice.common.exception.foodrequest;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class FoodRequestMismatchException extends BusinessException {

    public FoodRequestMismatchException() {
        super(ErrorCode.FOOD_REQUEST_MISMATCH);
    }
}
