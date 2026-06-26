package com.foodservice.domain.foodrequest.dto;

import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;

public record MyFoodRequestResponse(Long requestFoodId, FoodRequestStatus status) {

    public static MyFoodRequestResponse of(FoodRequest request) {
        return new MyFoodRequestResponse(request.getRequestFoodId(), request.getStatus());
    }
}
