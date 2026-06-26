package com.foodservice.domain.food.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class FoodCreateResponse {

    private Long foodId;

    public static FoodCreateResponse of(Long foodId) {
        return new FoodCreateResponse(foodId);
    }
}
