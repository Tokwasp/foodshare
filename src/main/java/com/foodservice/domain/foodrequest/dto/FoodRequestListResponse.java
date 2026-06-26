package com.foodservice.domain.foodrequest.dto;

import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;

public record FoodRequestListResponse(
        Long requestFoodId,
        Long memberId,
        String requesterNickName,
        FoodRequestStatus status
) {
    public static FoodRequestListResponse of(FoodRequest request, String requesterNickName) {
        return new FoodRequestListResponse(
                request.getRequestFoodId(),
                request.getMemberId(),
                requesterNickName,
                request.getStatus()
        );
    }
}
