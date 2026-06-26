package com.foodservice.domain.food.controller;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.constant.SessionConst;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import com.foodservice.domain.image.repository.ImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FoodUpdateControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockHttpSession loginSession(Long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, memberId);
        return session;
    }

    private Food saveFood(Long ownerId, int capacity, int approvedCount) {
        return foodRepository.save(Food.builder()
                .memberId(ownerId)
                .foodName("원래이름")
                .details("원래상세")
                .capacity(capacity)
                .approvedCount(approvedCount)
                .exStatus(ExStatus.IN_PROGRESS)
                .expired(LocalDateTime.now().plusDays(3))
                .build());
    }

    private Image saveImage(Long foodId, ImageType type, String stored) {
        return imageRepository.save(Image.builder()
                .foodId(foodId).originalName(stored + ".png").storedName(stored).imageType(type).build());
    }

    @Test
    @DisplayName("PATCH /api/v1/foods/{foodId} — 소유자가 이름/정원/상세를 수정하고 기존 이미지를 삭제한다(JSON).")
    void updateFood_success() throws Exception {
        Long owner = 1L;
        Food food = saveFood(owner, 3, 1);
        Image toDelete = saveImage(food.getFoodId(), ImageType.BASIC, "old-basic");

        Map<String, Object> body = Map.of(
                "foodName", "수정된이름",
                "capacity", 5,
                "details", "수정된상세",
                "deleteImageIds", List.of(toDelete.getImageId())
        );

        mockMvc.perform(patch("/api/v1/foods/{foodId}", food.getFoodId())
                        .session(loginSession(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("물품이 수정되었습니다."));

        Food updated = foodRepository.findById(food.getFoodId()).orElseThrow();
        assertThat(updated.getFoodName()).isEqualTo("수정된이름");
        assertThat(updated.getCapacity()).isEqualTo(5);
        assertThat(updated.getDetails()).isEqualTo("수정된상세");
        assertThat(imageRepository.findById(toDelete.getImageId()).orElseThrow().isDeleted()).isTrue();
    }

    @Test
    @DisplayName("PATCH — 정원이 현재 승인 인원보다 작으면 400 CAPACITY_TOO_SMALL.")
    void updateFood_capacityTooSmall_returns400() throws Exception {
        Long owner = 1L;
        Food food = saveFood(owner, 3, 2);
        Map<String, Object> body = Map.of("foodName", "이름", "capacity", 1, "details", "상세");

        mockMvc.perform(patch("/api/v1/foods/{foodId}", food.getFoodId())
                        .session(loginSession(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("CAPACITY_TOO_SMALL"));
    }

    @Test
    @DisplayName("PATCH — 소유자가 아니면 403 FOOD_FORBIDDEN.")
    void updateFood_forbidden_returns403() throws Exception {
        Food food = saveFood(1L, 3, 0);
        Map<String, Object> body = Map.of("foodName", "이름", "capacity", 2, "details", "상세");

        mockMvc.perform(patch("/api/v1/foods/{foodId}", food.getFoodId())
                        .session(loginSession(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH — expiredImageId가 해당 물품의 유효한 이미지가 아니면 404 EXPIRED_IMAGE_NOT_FOUND.")
    void updateFood_invalidExpiredImageId_returns404() throws Exception {
        Long owner = 1L;
        Food food = saveFood(owner, 3, 0);
        Map<String, Object> body = Map.of(
                "foodName", "이름", "capacity", 2, "details", "상세",
                "expiredImageId", 99999);

        mockMvc.perform(patch("/api/v1/foods/{foodId}", food.getFoodId())
                        .session(loginSession(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("EXPIRED_IMAGE_NOT_FOUND"));
    }
}
