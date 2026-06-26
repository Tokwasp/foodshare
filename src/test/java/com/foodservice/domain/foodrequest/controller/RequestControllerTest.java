package com.foodservice.domain.foodrequest.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.exception.food.FoodForbiddenException;
import com.foodservice.common.exception.foodrequest.InvalidRequestStatusException;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.facade.FoodFacade;
import com.foodservice.domain.foodrequest.dto.RequestApproveResponse;
import com.foodservice.domain.foodrequest.dto.RequestRejectResponse;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RequestController.class)
class RequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FoodFacade foodFacade;

    private MockHttpSession sessionOf(Long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, memberId);
        return session;
    }

    @Test
    @DisplayName("PATCH /api/v1/requests/{id}/approve — 수락 시 200과 status/foodStatusTx를 반환한다.")
    void approve_returns200() throws Exception {
        Long memberId = 1L;
        Long requestFoodId = 500L;
        given(foodFacade.approveRequest(memberId, requestFoodId))
                .willReturn(RequestApproveResponse.of(requestFoodId, FoodRequestStatus.APPROVED, ExStatus.IN_PROGRESS));

        mockMvc.perform(patch("/api/v1/requests/{id}/approve", requestFoodId)
                        .session(sessionOf(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestFoodId").value(500))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.foodStatusTx").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("PATCH /api/v1/requests/{id}/reject — 거절 시 200과 status를 반환한다.")
    void reject_returns200() throws Exception {
        Long memberId = 1L;
        Long requestFoodId = 500L;
        given(foodFacade.rejectRequest(memberId, requestFoodId))
                .willReturn(RequestRejectResponse.of(requestFoodId, FoodRequestStatus.REJECTED));

        mockMvc.perform(patch("/api/v1/requests/{id}/reject", requestFoodId)
                        .session(sessionOf(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestFoodId").value(500))
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    @DisplayName("PATCH /approve — 등록자가 아니면 403.")
    void approve_returns403_whenNotOwner() throws Exception {
        Long memberId = 2L;
        Long requestFoodId = 500L;
        willThrow(new FoodForbiddenException()).given(foodFacade).approveRequest(memberId, requestFoodId);

        mockMvc.perform(patch("/api/v1/requests/{id}/approve", requestFoodId)
                        .session(sessionOf(memberId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /reject — 이미 처리된 신청이면 409.")
    void reject_returns409_whenInvalidStatus() throws Exception {
        Long memberId = 1L;
        Long requestFoodId = 500L;
        willThrow(new InvalidRequestStatusException()).given(foodFacade).rejectRequest(memberId, requestFoodId);

        mockMvc.perform(patch("/api/v1/requests/{id}/reject", requestFoodId)
                        .session(sessionOf(memberId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST_STATUS"));
    }
}
