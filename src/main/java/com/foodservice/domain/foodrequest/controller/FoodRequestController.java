package com.foodservice.domain.foodrequest.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.response.ApiResponse;
import com.foodservice.domain.foodrequest.dto.FoodRequestListResponse;
import com.foodservice.domain.foodrequest.dto.FoodRequestResponse;
import com.foodservice.domain.foodrequest.dto.MyFoodRequestResponse;
import com.foodservice.domain.food.facade.FoodFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/foods/{foodId}/requests")
@RequiredArgsConstructor
public class FoodRequestController {

    private final FoodFacade foodFacade;

    @GetMapping("/me")
    public ApiResponse<MyFoodRequestResponse> getMyRequest(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId
    ) {
        return ApiResponse.success(foodFacade.getMyRequest(memberId, foodId), "내 신청 조회가 완료되었습니다.");
    }

    @GetMapping
    public ApiResponse<List<FoodRequestListResponse>> getRequests(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId
    ) {
        return ApiResponse.success(foodFacade.getRequests(memberId, foodId), "신청 목록 조회가 완료되었습니다.");
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<FoodRequestResponse> requestFood(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId
    ) {
        return ApiResponse.success(foodFacade.requestFood(memberId, foodId), "음식 신청이 완료되었습니다.");
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/{requestId}/approve")
    public void approveRequest(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId,
            @PathVariable Long requestId
    ) {
        foodFacade.approveRequest(memberId, foodId, requestId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PatchMapping("/{requestId}/reject")
    public void rejectRequest(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId,
            @PathVariable Long requestId
    ) {
        foodFacade.rejectRequest(memberId, foodId, requestId);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{requestId}")
    public void cancelRequest(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId,
            @PathVariable Long requestId
    ) {
        foodFacade.cancelRequest(memberId, foodId, requestId);
    }
}
