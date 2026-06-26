package com.foodservice.common.exception.food;

import com.foodservice.common.exception.BusinessException;
import com.foodservice.common.exception.ErrorCode;

public class FoodNotAvailableException extends BusinessException {

    public FoodNotAvailableException() {
        super(ErrorCode.FOOD_NOT_AVAILABLE);
    }
}
