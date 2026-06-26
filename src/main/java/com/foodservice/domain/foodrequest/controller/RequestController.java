package com.foodservice.domain.foodrequest.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.response.ApiResponse;
import com.foodservice.domain.food.facade.FoodFacade;
import com.foodservice.domain.foodrequest.dto.RequestApproveResponse;
import com.foodservice.domain.foodrequest.dto.RequestRejectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

@RestController
@RequestMapping("/api/v1/requests")
@RequiredArgsConstructor
public class RequestController {

    private final FoodFacade foodFacade;

    @PatchMapping("/{requestFoodId}/approve")
    public ApiResponse<RequestApproveResponse> approve(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long requestFoodId
    ) {
        return ApiResponse.success(foodFacade.approveRequest(memberId, requestFoodId), "요청을 수락했습니다.");
    }

    @PatchMapping("/{requestFoodId}/reject")
    public ApiResponse<RequestRejectResponse> reject(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long requestFoodId
    ) {
        return ApiResponse.success(foodFacade.rejectRequest(memberId, requestFoodId), "요청을 거절했습니다.");
    }
}
