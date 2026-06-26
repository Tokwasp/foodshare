package com.foodservice.domain.foodrequest.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.exception.food.FoodForbiddenException;
import com.foodservice.common.exception.food.FoodNotAvailableException;
import com.foodservice.common.exception.foodrequest.FoodRequestAlreadyExistsException;
import com.foodservice.common.exception.foodrequest.FoodRequestNotFoundException;
import com.foodservice.common.exception.foodrequest.InvalidRequestStatusException;
import com.foodservice.domain.foodrequest.dto.FoodRequestListResponse;
import com.foodservice.domain.foodrequest.dto.MyFoodRequestResponse;
import com.foodservice.domain.foodrequest.dto.FoodRequestResponse;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;
import com.foodservice.domain.food.facade.FoodFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FoodRequestController.class)
class FoodRequestControllerTest {

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
    @DisplayName("POST /api/v1/foods/{foodId}/requests — 정상 신청 시 201과 requestFoodId를 반환한다.")
    void requestFood_returns201() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        given(foodFacade.requestFood(memberId, foodId)).willReturn(FoodRequestResponse.of(10L));

        // when & then
        mockMvc.perform(post("/api/v1/foods/{foodId}/requests", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.requestFoodId").value(10L))
                .andExpect(jsonPath("$.message").value("음식 신청이 완료되었습니다."));
    }

    @Test
    @DisplayName("POST — 본인 음식 신청 시 403을 반환한다.")
    void requestFood_returns403_whenOwnFood() throws Exception {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        willThrow(new FoodForbiddenException()).given(foodFacade).requestFood(memberId, foodId);

        // when & then
        mockMvc.perform(post("/api/v1/foods/{foodId}/requests", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("POST — IN_PROGRESS 아닌 음식 신청 시 400을 반환한다.")
    void requestFood_returns400_whenFoodNotAvailable() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        willThrow(new FoodNotAvailableException()).given(foodFacade).requestFood(memberId, foodId);

        // when & then
        mockMvc.perform(post("/api/v1/foods/{foodId}/requests", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("FOOD_NOT_AVAILABLE"));
    }

    @Test
    @DisplayName("POST — 중복 신청 시 409를 반환한다.")
    void requestFood_returns409_whenAlreadyExists() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        willThrow(new FoodRequestAlreadyExistsException()).given(foodFacade).requestFood(memberId, foodId);

        // when & then
        mockMvc.perform(post("/api/v1/foods/{foodId}/requests", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("FOOD_REQUEST_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("PATCH /{requestId}/approve — 정상 승인 시 204를 반환한다.")
    void approveRequest_returns204() throws Exception {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Long requestId = 10L;
        willDoNothing().given(foodFacade).approveRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(patch("/api/v1/foods/{foodId}/requests/{requestId}/approve", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /{requestId}/approve — 등록자 아닌 경우 403을 반환한다.")
    void approveRequest_returns403_whenNotOwner() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Long requestId = 10L;
        willThrow(new FoodForbiddenException()).given(foodFacade).approveRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(patch("/api/v1/foods/{foodId}/requests/{requestId}/approve", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("PATCH /{requestId}/approve — 존재하지 않는 신청 승인 시 404를 반환한다.")
    void approveRequest_returns404_whenNotFound() throws Exception {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Long requestId = 99L;
        willThrow(new FoodRequestNotFoundException()).given(foodFacade).approveRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(patch("/api/v1/foods/{foodId}/requests/{requestId}/approve", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("FOOD_REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /{requestId}/reject — 정상 거절 시 204를 반환한다.")
    void rejectRequest_returns204() throws Exception {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Long requestId = 10L;
        willDoNothing().given(foodFacade).rejectRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(patch("/api/v1/foods/{foodId}/requests/{requestId}/reject", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /{requestId}/reject — 등록자 아닌 경우 403을 반환한다.")
    void rejectRequest_returns403_whenNotOwner() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Long requestId = 10L;
        willThrow(new FoodForbiddenException()).given(foodFacade).rejectRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(patch("/api/v1/foods/{foodId}/requests/{requestId}/reject", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /requests — 등록자가 조회 시 200과 신청 목록을 반환한다.")
    void getRequests_returns200() throws Exception {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        List<FoodRequestListResponse> responses = List.of(
                new FoodRequestListResponse(10L, 2L, "신청자A", FoodRequestStatus.REQUEST),
                new FoodRequestListResponse(11L, 3L, "신청자B", FoodRequestStatus.APPROVED)
        );
        given(foodFacade.getRequests(memberId, foodId)).willReturn(responses);

        // when & then
        mockMvc.perform(get("/api/v1/foods/{foodId}/requests", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].requestFoodId").value(10L))
                .andExpect(jsonPath("$.data[0].requesterNickName").value("신청자A"))
                .andExpect(jsonPath("$.data[0].status").value("REQUEST"))
                .andExpect(jsonPath("$.message").value("신청 목록 조회가 완료되었습니다."));
    }

    @Test
    @DisplayName("GET /requests — 등록자 아닌 경우 403을 반환한다.")
    void getRequests_returns403_whenNotOwner() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        willThrow(new FoodForbiddenException()).given(foodFacade).getRequests(memberId, foodId);

        // when & then
        mockMvc.perform(get("/api/v1/foods/{foodId}/requests", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("DELETE /{requestId} — 신청자 본인이 취소 시 204를 반환한다.")
    void cancelRequest_returns204() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Long requestId = 10L;
        willDoNothing().given(foodFacade).cancelRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(delete("/api/v1/foods/{foodId}/requests/{requestId}", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /{requestId} — 신청자 본인이 아닌 경우 403을 반환한다.")
    void cancelRequest_returns403_whenNotRequester() throws Exception {
        // given
        Long memberId = 3L;
        Long foodId = 1L;
        Long requestId = 10L;
        willThrow(new FoodForbiddenException()).given(foodFacade).cancelRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(delete("/api/v1/foods/{foodId}/requests/{requestId}", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("DELETE /{requestId} — 존재하지 않는 신청 취소 시 404를 반환한다.")
    void cancelRequest_returns404_whenNotFound() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Long requestId = 99L;
        willThrow(new FoodRequestNotFoundException()).given(foodFacade).cancelRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(delete("/api/v1/foods/{foodId}/requests/{requestId}", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("FOOD_REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /{requestId} — REQUEST가 아닌 신청 취소 시 409를 반환한다.")
    void cancelRequest_returns409_whenInvalidStatus() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Long requestId = 10L;
        willThrow(new InvalidRequestStatusException()).given(foodFacade).cancelRequest(memberId, foodId, requestId);

        // when & then
        mockMvc.perform(delete("/api/v1/foods/{foodId}/requests/{requestId}", foodId, requestId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST_STATUS"));
    }

    @Test
    @DisplayName("GET /requests/me — 본인 신청 상태를 반환한다.")
    void getMyRequest_returns200() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        MyFoodRequestResponse response = new MyFoodRequestResponse(10L, FoodRequestStatus.REQUEST);
        given(foodFacade.getMyRequest(memberId, foodId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/foods/{foodId}/requests/me", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestFoodId").value(10L))
                .andExpect(jsonPath("$.data.status").value("REQUEST"))
                .andExpect(jsonPath("$.message").value("내 신청 조회가 완료되었습니다."));
    }

    @Test
    @DisplayName("GET /requests/me — 신청이 없으면 404를 반환한다.")
    void getMyRequest_returns404_whenNotFound() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        willThrow(new FoodRequestNotFoundException()).given(foodFacade).getMyRequest(memberId, foodId);

        // when & then
        mockMvc.perform(get("/api/v1/foods/{foodId}/requests/me", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("FOOD_REQUEST_NOT_FOUND"));
    }
}
