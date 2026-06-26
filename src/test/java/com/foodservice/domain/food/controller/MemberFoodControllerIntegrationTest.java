package com.foodservice.domain.food.controller;

import com.foodservice.IntegrationTestSupport;
import com.foodservice.common.constant.SessionConst;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MemberFoodControllerIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FoodRepository foodRepository;

    private MockHttpSession loginSession(Long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, memberId);
        return session;
    }

    private Food saveFood(Long memberId, ExStatus status, String name) {
        return foodRepository.save(Food.builder()
                .memberId(memberId)
                .foodName(name)
                .details("상세")
                .capacity(3)
                .exStatus(status)
                .expired(LocalDateTime.now().plusDays(3))
                .build());
    }

    @Test
    @DisplayName("GET /api/v1/members/me/foods — 로그인 회원이 등록한 물품을 상태와 무관하게 최신순으로 반환한다.")
    void getMyFoods_returnsOwnFoodsAllStatuses() throws Exception {
        Long me = 1L;
        saveFood(me, ExStatus.IN_PROGRESS, "내음식A");
        Food latest = saveFood(me, ExStatus.COMPLETED, "내음식B");
        saveFood(2L, ExStatus.IN_PROGRESS, "남의음식");

        mockMvc.perform(get("/api/v1/members/me/foods").session(loginSession(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].foodId").value(latest.getFoodId()))
                .andExpect(jsonPath("$.data[0].foodName").value("내음식B"))
                .andExpect(jsonPath("$.data[0].statusTx").value("COMPLETED"))
                .andExpect(jsonPath("$.data[1].foodName").value("내음식A"))
                .andExpect(jsonPath("$.message").value("조회에 성공했습니다."));
    }

    @Test
    @DisplayName("GET /api/v1/members/me/foods — soft delete된 물품은 제외한다.")
    void getMyFoods_excludesDeleted() throws Exception {
        Long me = 1L;
        saveFood(me, ExStatus.IN_PROGRESS, "살아있는음식");
        Food deleted = saveFood(me, ExStatus.IN_PROGRESS, "삭제된음식");
        deleted.delete();
        foodRepository.save(deleted);

        mockMvc.perform(get("/api/v1/members/me/foods").session(loginSession(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].foodName").value("살아있는음식"));
    }
}
