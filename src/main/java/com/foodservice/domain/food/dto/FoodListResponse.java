package com.foodservice.domain.food.dto;

import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import lombok.Getter;

import java.time.LocalDate;
import java.util.Map;

@Getter
public class FoodListResponse {

    private final Long foodId;
    private final String foodName;
    private final LocalDate expired;
    private final Integer capacity;
    private final Integer approvedCount;
    private final ExStatus statusTx;
    private final String thumbnailUrl;

    private FoodListResponse(Food food, String thumbnailUrl) {
        this.foodId = food.getFoodId();
        this.foodName = food.getFoodName();
        this.expired = food.getExpired().toLocalDate();
        this.capacity = food.getCapacity();
        this.approvedCount = food.getApprovedCount();
        this.statusTx = food.getExStatus();
        this.thumbnailUrl = thumbnailUrl;
    }

    public static FoodListResponse of(Food food, Map<Long, String> thumbnailUrls) {
        return new FoodListResponse(food, thumbnailUrls.get(food.getFoodId()));
    }
}
