package com.foodservice.common.exception.foodrequest;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class FoodRequestAlreadyExistsException extends BusinessException {

    public FoodRequestAlreadyExistsException() {
        super(ErrorCode.FOOD_REQUEST_ALREADY_EXISTS);
    }
}
