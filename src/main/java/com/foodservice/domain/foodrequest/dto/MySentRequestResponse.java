package com.foodservice.domain.foodrequest.dto;

import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;

public record MySentRequestResponse(
        Long requestFoodId,
        Long foodId,
        String foodName,
        String thumbnailUrl,
        FoodRequestStatus status
) {
    public static MySentRequestResponse of(FoodRequest request, String foodName, String thumbnailUrl) {
        return new MySentRequestResponse(
                request.getRequestFoodId(),
                request.getFoodId(),
                foodName,
                thumbnailUrl,
                request.getStatus()
        );
    }
}
