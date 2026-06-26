package com.foodservice.domain.food.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.response.ApiResponse;
import com.foodservice.domain.food.dto.FoodListResponse;
import com.foodservice.domain.food.facade.FoodFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

import java.util.List;

@RestController
@RequestMapping("/api/v1/members/me/foods")
@RequiredArgsConstructor
public class MemberFoodController {

    private final FoodFacade foodFacade;

    @GetMapping
    public ApiResponse<List<FoodListResponse>> getMyFoods(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId
    ) {
        return ApiResponse.success(foodFacade.getMyFoods(memberId), "조회에 성공했습니다.");
    }
}
