package com.foodservice.domain.food.controller;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FoodListControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodRepository foodRepository;

    private void saveFood(ExStatus status, String name) {
        foodRepository.save(Food.builder()
                .memberId(1L)
                .foodName(name)
                .details("상세")
                .capacity(3)
                .exStatus(status)
                .expired(LocalDateTime.now().plusDays(3))
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/foods — status 미지정이면 삭제 제외 전체 상태를 반환한다.")
    void getFoods_noStatus_returnsAllActive() throws Exception {
        saveFood(ExStatus.IN_PROGRESS, "진행중");
        saveFood(ExStatus.COMPLETED, "완료됨");

        mockMvc.perform(get("/api/v1/foods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/foods?status=COMPLETED — 해당 상태만 반환한다.")
    void getFoods_withStatus_filters() throws Exception {
        saveFood(ExStatus.IN_PROGRESS, "진행중");
        saveFood(ExStatus.COMPLETED, "완료됨");

        mockMvc.perform(get("/api/v1/foods").param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].statusTx").value("COMPLETED"));
    }

    @Test
    @DisplayName("GET /api/v1/foods/recent — 기본 2개를 최신 등록순(foodId desc)으로 반환한다.")
    void getRecentFoods_default_returnsTwoLatest() throws Exception {
        saveFood(ExStatus.IN_PROGRESS, "첫번째");
        saveFood(ExStatus.IN_PROGRESS, "두번째");
        saveFood(ExStatus.IN_PROGRESS, "세번째");

        mockMvc.perform(get("/api/v1/foods/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].foodName").value("세번째"))
                .andExpect(jsonPath("$.data[1].foodName").value("두번째"));
    }

    @Test
    @DisplayName("GET /api/v1/foods/recent?size=1 — size 파라미터만큼 반환한다.")
    void getRecentFoods_withSize() throws Exception {
        saveFood(ExStatus.IN_PROGRESS, "첫번째");
        saveFood(ExStatus.COMPLETED, "두번째");

        mockMvc.perform(get("/api/v1/foods/recent").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].foodName").value("두번째"));
    }

    @Test
    @DisplayName("GET /api/v1/foods/recent?size=0 — 잘못된 size는 400을 반환한다.")
    void getRecentFoods_invalidSize_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/foods/recent").param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}
