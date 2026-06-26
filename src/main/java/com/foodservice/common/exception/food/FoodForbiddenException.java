package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class FoodForbiddenException extends BusinessException {

    public FoodForbiddenException() {
        super(ErrorCode.FOOD_FORBIDDEN);
    }
}
