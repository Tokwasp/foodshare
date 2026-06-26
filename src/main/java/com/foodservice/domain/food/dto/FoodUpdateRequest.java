package com.foodservice.domain.food.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class FoodUpdateRequest {

    @NotBlank(message = "음식 이름은 필수입니다.")
    private String foodName;

    @NotNull(message = "나눔 가능 수량은 필수입니다.")
    @Min(value = 1, message = "나눔 가능 수량은 최소 1이어야 합니다.")
    private Integer capacity;

    @NotBlank(message = "상세 내용은 필수입니다.")
    private String details;

    // 소비기한 사진을 교체할 경우에만 전달 (선택). 해당 물품의 유효한 이미지여야 한다.
    private Long expiredImageId;

    // 삭제할 기존 이미지 ID 목록 (선택)
    private List<Long> deleteImageIds;
}
