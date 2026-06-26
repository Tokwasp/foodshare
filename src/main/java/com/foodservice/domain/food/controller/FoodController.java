package com.foodservice.domain.food.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.exception.food.InvalidPageSizeException;
import com.foodservice.common.exception.food.InvalidSortFieldException;
import com.foodservice.common.response.ApiResponse;
import com.foodservice.common.response.PageResponse;
import com.foodservice.domain.food.dto.ExpirationDateResponse;
import com.foodservice.domain.food.dto.FoodCreateRequest;
import com.foodservice.domain.food.dto.FoodCreateResponse;
import com.foodservice.domain.food.dto.FoodDetailResponse;
import com.foodservice.domain.food.dto.FoodListResponse;
import com.foodservice.domain.food.dto.FoodUpdateRequest;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.facade.FoodFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/foods")
@RequiredArgsConstructor
public class FoodController {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("foodId", "expired", "capacity");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_RECENT_SIZE = 2;

    private final FoodFacade foodFacade;

    @PostMapping(value = "/expired-date", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ExpirationDateResponse> recognizeExpirationDate(
            @RequestPart("expiredImage") MultipartFile expiredImage
    ) {
        return ApiResponse.success(ExpirationDateResponse.of(foodFacade.recognizeExpirationDate(expiredImage)), "소비기한 분석이 완료되었습니다.");
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FoodCreateResponse> registerFood(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @Valid @RequestPart("request") FoodCreateRequest request,
            @RequestPart("expiredImage") MultipartFile expiredImage,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        Long foodId = foodFacade.registerFood(memberId, request, expiredImage, images);
        return ApiResponse.success(FoodCreateResponse.of(foodId), "음식 등록이 완료되었습니다.");
    }

    // 메인 화면 히어로 노출용: 가장 최근에 등록된 음식 N개(기본 2개)의 대표사진 + 요약 정보
    @GetMapping("/recent")
    public ApiResponse<List<FoodListResponse>> getRecentFoods(
            @RequestParam(defaultValue = "" + DEFAULT_RECENT_SIZE) int size
    ) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException();
        }
        return ApiResponse.success(foodFacade.getRecentFoods(size), "최근 등록된 음식 조회가 완료되었습니다.");
    }

    @GetMapping("/{foodId}")
    public ApiResponse<FoodDetailResponse> getFoodDetail(@PathVariable Long foodId) {
        return ApiResponse.success(foodFacade.getFoodDetail(foodId), "음식 상세 조회가 완료되었습니다.");
    }

    // 물품 수정은 사진 입력이 없으므로(이름·정원·상세만) JSON 본문으로 받는다.
    // 소비기한 사진 교체(expiredImageId)·기존 이미지 삭제(deleteImageIds)는 JSON 필드로 전달 가능.
    @PatchMapping("/{foodId}")
    public ApiResponse<Void> updateFood(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId,
            @Valid @RequestBody FoodUpdateRequest request
    ) {
        foodFacade.updateFood(memberId, foodId, request, null);
        return ApiResponse.success(null, "물품이 수정되었습니다.");
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{foodId}")
    public void deleteFood(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId
    ) {
        foodFacade.deleteFood(memberId, foodId);
    }

    @GetMapping
    public ApiResponse<PageResponse<FoodListResponse>> getFoods(
            @RequestParam(required = false) ExStatus status,
            @PageableDefault(size = 10, sort = "foodId", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        validatePageable(pageable);
        return ApiResponse.success(foodFacade.getFoods(status, pageable), "음식 목록 조회가 완료되었습니다.");
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new InvalidPageSizeException();
        }
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_FIELDS.contains(order.getProperty())) {
                throw new InvalidSortFieldException();
            }
        });
    }
}
