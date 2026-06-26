package com.foodservice.domain.foodrequest.dto;

public record FoodRequestResponse(Long requestFoodId) {

    public static FoodRequestResponse of(Long requestFoodId) {
        return new FoodRequestResponse(requestFoodId);
    }
}
