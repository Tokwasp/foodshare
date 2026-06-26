package com.foodservice.domain.food.dto;

import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.image.dto.response.ImageResponse;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
public class FoodDetailResponse {

    private final Long foodId;
    private final Long memberId;
    private final String ownerNickName;
    private final String foodName;
    private final String details;
    private final Integer capacity;
    private final Integer approvedCount;
    private final ExStatus statusTx;
    private final LocalDate expired;
    private final String region;
    private final List<ImageResponse> images;

    private FoodDetailResponse(Food food, String ownerNickName, List<ImageResponse> images) {
        this.foodId = food.getFoodId();
        this.memberId = food.getMemberId();
        this.ownerNickName = ownerNickName;
        this.foodName = food.getFoodName();
        this.details = food.getDetails();
        this.capacity = food.getCapacity();
        this.approvedCount = food.getApprovedCount();
        this.statusTx = food.getExStatus();
        this.expired = food.getExpired().toLocalDate();
        this.region = food.getRegion();
        this.images = images;
    }

    public static FoodDetailResponse of(Food food, String ownerNickName, List<ImageResponse> images) {
        return new FoodDetailResponse(food, ownerNickName, images);
    }
}
