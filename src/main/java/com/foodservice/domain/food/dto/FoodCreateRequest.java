package com.foodservice.domain.food.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class FoodCreateRequest {

    @NotBlank(message = "음식 이름은 필수입니다.")
    private String foodName;

    @NotNull(message = "나눔 가능 수량은 필수입니다.")
    @Min(value = 1, message = "나눔 가능 수량은 최소 1이어야 합니다.")
    private Integer capacity;

    @NotBlank(message = "상세 내용은 필수입니다.")
    private String details;

    // 선택 입력 — 향후 지역 필터링 기능에 활용
    private String region;

    // 소비기한(AI 인식값). expired-date 단계에서 받은 날짜를 그대로 전송한다.
    @NotNull(message = "소비기한은 필수입니다.")
    private LocalDate expired;
}
