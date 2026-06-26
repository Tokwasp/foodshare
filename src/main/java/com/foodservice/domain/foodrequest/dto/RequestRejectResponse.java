package com.foodservice.domain.foodrequest.dto;

import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;

public record RequestRejectResponse(
        Long requestFoodId,
        FoodRequestStatus status
) {
    public static RequestRejectResponse of(Long requestFoodId, FoodRequestStatus status) {
        return new RequestRejectResponse(requestFoodId, status);
    }
}
