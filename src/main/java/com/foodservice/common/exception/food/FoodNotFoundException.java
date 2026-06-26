package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class FoodNotFoundException extends BusinessException {
    public FoodNotFoundException() {
        super(ErrorCode.FOOD_NOT_FOUND);
    }
}
