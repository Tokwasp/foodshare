package com.foodservice.domain.image.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.response.ApiResponse;
import com.foodservice.domain.food.facade.FoodFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ImageController {

    private final FoodFacade foodFacade;

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/foods/expired-date/{foodId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Long> uploadImage(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long foodId,
            @RequestPart("file") MultipartFile file
    ) {
        Long imageId = foodFacade.uploadExpiredImage(memberId, foodId, file);
        return ApiResponse.success(HttpStatus.CREATED, imageId, "이미지 업로드가 완료되었습니다.");
    }

    @DeleteMapping("/images/{imageId}")
    public ApiResponse<Void> deleteImage(
            @SessionAttribute(SessionConst.LOGIN_MEMBER_ID) Long memberId,
            @PathVariable Long imageId
    ) {
        foodFacade.deleteImage(memberId, imageId);
        return ApiResponse.success(null, "이미지가 삭제되었습니다.");
    }
}
