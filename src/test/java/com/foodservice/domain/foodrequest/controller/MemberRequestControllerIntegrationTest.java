package com.foodservice.domain.foodrequest.controller;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.constant.SessionConst;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.repository.FoodRequestRepository;
import com.foodservice.domain.image.entity.Image;
import com.foodservice.domain.image.entity.ImageType;
import com.foodservice.domain.image.repository.ImageRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MemberRequestControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodRepository foodRepository;

    @Autowired
    private FoodRequestRepository foodRequestRepository;

    @Autowired
    private ImageRepository imageRepository;

    private MockHttpSession loginSession(Long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, memberId);
        return session;
    }

    private Food saveFood(Long ownerId, String name) {
        return foodRepository.save(Food.builder()
                .memberId(ownerId)
                .foodName(name)
                .details("상세")
                .capacity(3)
                .exStatus(ExStatus.IN_PROGRESS)
                .expired(LocalDateTime.now().plusDays(3))
                .build());
    }

    private FoodRequest saveRequest(Long foodId, Long requesterId) {
        return foodRequestRepository.save(FoodRequest.builder().foodId(foodId).memberId(requesterId).build());
    }

    private Image saveImage(Long foodId, String storedName, ImageType type) {
        return imageRepository.save(Image.builder()
                .foodId(foodId)
                .originalName(storedName)
                .storedName(storedName)
                .imageType(type)
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/members/me/requests — 로그인 회원이 보낸 요청을 물품명과 함께 최신순으로 반환한다.")
    void getMyRequests_returnsOwnSentRequests() throws Exception {
        Long me = 10L;
        Food foodA = saveFood(1L, "사과");
        Food foodB = saveFood(2L, "바나나");
        saveImage(foodB.getFoodId(), "banana-basic.png", ImageType.BASIC); // foodB 대표 썸네일
        saveRequest(foodA.getFoodId(), me);
        FoodRequest latest = saveRequest(foodB.getFoodId(), me);
        saveRequest(foodA.getFoodId(), 99L); // 다른 회원 요청 — 제외

        mockMvc.perform(get("/api/v1/members/me/requests").session(loginSession(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].requestFoodId").value(latest.getRequestFoodId()))
                .andExpect(jsonPath("$.data[0].foodId").value(foodB.getFoodId()))
                .andExpect(jsonPath("$.data[0].foodName").value("바나나"))
                .andExpect(jsonPath("$.data[0].thumbnailUrl").value("https://stub-s3-bucket.com/images/banana-basic.png"))
                .andExpect(jsonPath("$.data[0].status").value("REQUEST"))
                .andExpect(jsonPath("$.data[1].foodName").value("사과"))
                .andExpect(jsonPath("$.data[1].thumbnailUrl").value(nullValue())) // foodA: 이미지 없음 → null
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다."));
    }

    @Test
    @DisplayName("GET /api/v1/members/me/requests — 철회(soft delete)된 요청은 제외한다.")
    void getMyRequests_excludesCancelled() throws Exception {
        Long me = 10L;
        Food food = saveFood(1L, "포도");
        saveRequest(food.getFoodId(), me);
        FoodRequest cancelled = saveRequest(food.getFoodId(), me);
        cancelled.cancel();
        foodRequestRepository.save(cancelled);

        mockMvc.perform(get("/api/v1/members/me/requests").session(loginSession(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }
}
