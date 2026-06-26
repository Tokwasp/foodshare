package com.foodservice.domain.foodrequest.dto;

import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;

public record RequestApproveResponse(
        Long requestFoodId,
        FoodRequestStatus status,
        ExStatus foodStatusTx
) {
    public static RequestApproveResponse of(Long requestFoodId, FoodRequestStatus status, ExStatus foodStatusTx) {
        return new RequestApproveResponse(requestFoodId, status, foodStatusTx);
    }
}
