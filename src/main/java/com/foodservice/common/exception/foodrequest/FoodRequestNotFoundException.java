package com.foodservice.common.exception.foodrequest;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class FoodRequestNotFoundException extends BusinessException {

    public FoodRequestNotFoundException() {
        super(ErrorCode.FOOD_REQUEST_NOT_FOUND);
    }
}
