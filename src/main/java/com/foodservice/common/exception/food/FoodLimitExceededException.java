package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class FoodLimitExceededException extends BusinessException {

    public FoodLimitExceededException() {
        super(ErrorCode.FOOD_LIMIT_EXCEEDED);
    }
}
