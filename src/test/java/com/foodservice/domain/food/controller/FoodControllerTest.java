package com.foodservice.domain.food.controller;

import com.foodservice.common.constant.SessionConst;
import com.foodservice.common.exception.food.FoodForbiddenException;
import com.foodservice.common.exception.food.FoodLimitExceededException;
import com.foodservice.common.exception.food.FoodNotFoundException;
import com.foodservice.common.response.PageResponse;
import com.foodservice.domain.food.dto.FoodCreateRequest;
import com.foodservice.domain.food.dto.FoodDetailResponse;
import com.foodservice.domain.food.dto.FoodListResponse;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.facade.FoodFacade;
import com.foodservice.domain.image.dto.response.ImageResponse;
import com.foodservice.domain.image.entity.ImageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FoodController.class)
class FoodControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FoodFacade foodFacade;

    private MockHttpSession sessionOf(Long memberId) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionConst.LOGIN_MEMBER_ID, memberId);
        return session;
    }

    @Test
    @DisplayName("유효한 요청으로 음식을 등록하면 foodId와 201을 반환한다.")
    void registerFood_Success() throws Exception {
        // given
        Long memberId = 1L;
        Long expectedFoodId = 10L;
        FoodCreateRequest request = new FoodCreateRequest("초코 스무디", 3, "상세 내용", null, LocalDate.now().plusDays(7));

        MockMultipartFile expiredImage = new MockMultipartFile(
                "expiredImage", "exp.png", "image/png", "exp-content".getBytes()
        );
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        given(foodFacade.registerFood(eq(memberId), any(FoodCreateRequest.class), any(), any()))
                .willReturn(expectedFoodId);

        // when & then
        mockMvc.perform(multipart("/api/v1/foods")
                        .file(expiredImage)
                        .file(requestPart)
                        .session(sessionOf(memberId))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.foodId").value(expectedFoodId))
                .andExpect(jsonPath("$.message").value("음식 등록이 완료되었습니다."));
    }

    @Test
    @DisplayName("활성 음식 개수 한도 초과 시 400과 ProblemDetails를 반환한다.")
    void registerFood_Returns400_WhenFoodLimitExceeded() throws Exception {
        // given
        Long memberId = 1L;
        FoodCreateRequest request = new FoodCreateRequest("초코 스무디", 3, "상세 내용", null, LocalDate.now().plusDays(7));

        MockMultipartFile expiredImage = new MockMultipartFile(
                "expiredImage", "exp.png", "image/png", "exp-content".getBytes()
        );
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        willThrow(new FoodLimitExceededException())
                .given(foodFacade).registerFood(eq(memberId), any(FoodCreateRequest.class), any(), any());

        // when & then
        mockMvc.perform(multipart("/api/v1/foods")
                        .file(expiredImage)
                        .file(requestPart)
                        .session(sessionOf(memberId))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("활성 음식 등록 개수 한도를 초과했습니다."));
    }

    @Test
    @DisplayName("foodName이 빈 값이면 400과 검증 오류 메시지를 반환한다.")
    void registerFood_Returns400_WhenFoodNameIsBlank() throws Exception {
        // given
        Long memberId = 1L;
        FoodCreateRequest request = new FoodCreateRequest("", 3, "상세 내용", null, LocalDate.now().plusDays(7));

        MockMultipartFile expiredImage = new MockMultipartFile(
                "expiredImage", "exp.png", "image/png", "exp-content".getBytes()
        );
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        // when & then
        mockMvc.perform(multipart("/api/v1/foods")
                        .file(expiredImage)
                        .file(requestPart)
                        .session(sessionOf(memberId))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("GET /api/v1/foods/{foodId} — 음식 상세 정보를 반환한다.")
    void getFoodDetail_returnsDetail() throws Exception {
        // given
        Long foodId = 1L;
        Food food = Food.builder()
                .memberId(1L).foodName("초코 스무디").details("맛있음").capacity(3)
                .expired(LocalDateTime.now().plusDays(3))
                .build();
        FoodDetailResponse response = FoodDetailResponse.of(food, "등록자닉네임", List.of(
                ImageResponse.of(10L, "https://cdn/basic.png", ImageType.BASIC),
                ImageResponse.of(12L, "https://cdn/exp.png", ImageType.EXPIRED)
        ));
        given(foodFacade.getFoodDetail(foodId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/foods/{foodId}", foodId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.foodName").value("초코 스무디"))
                .andExpect(jsonPath("$.data.ownerNickName").value("등록자닉네임"))
                .andExpect(jsonPath("$.data.images[0].imageId").value(10))
                .andExpect(jsonPath("$.data.images[0].accessUrl").value("https://cdn/basic.png"))
                .andExpect(jsonPath("$.data.images[0].imageType").value("BASIC"))
                .andExpect(jsonPath("$.data.images[1].imageType").value("EXPIRED"))
                .andExpect(jsonPath("$.message").value("음식 상세 조회가 완료되었습니다."));
    }

    @Test
    @DisplayName("GET /api/v1/foods/{foodId} — 존재하지 않는 음식이면 404를 반환한다.")
    void getFoodDetail_returns404_whenNotFound() throws Exception {
        // given
        given(foodFacade.getFoodDetail(99L)).willThrow(new FoodNotFoundException());

        // when & then
        mockMvc.perform(get("/api/v1/foods/{foodId}", 99L))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("FOOD_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/v1/foods — 음식 목록을 페이징하여 반환한다.")
    void getFoods_returnsPagedList() throws Exception {
        // given
        Food food = Food.builder()
                .memberId(1L).foodName("초코 스무디").details("맛있음").capacity(3)
                .expired(LocalDateTime.now().plusDays(3))
                .build();
        PageResponse<FoodListResponse> page = PageResponse.of(
                new PageImpl<>(List.of(FoodListResponse.of(food, new HashMap<>())), PageRequest.of(0, 10), 1)
        );
        given(foodFacade.getFoods(any(), any(Pageable.class))).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/foods"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].foodName").value("초코 스무디"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.message").value("음식 목록 조회가 완료되었습니다."));
    }

    @Test
    @DisplayName("GET /api/v1/foods — size=100까지 허용한다.")
    void getFoods_allowsSize100() throws Exception {
        // given
        PageResponse<FoodListResponse> page = PageResponse.of(
                new PageImpl<>(List.of(), PageRequest.of(0, 100), 0)
        );
        given(foodFacade.getFoods(any(), any(Pageable.class))).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/v1/foods").param("size", "100"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/foods — size가 100을 초과하면 400 INVALID_PAGE_SIZE를 반환한다.")
    void getFoods_rejectsSizeOver100() throws Exception {
        // when & then
        mockMvc.perform(get("/api/v1/foods").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("INVALID_PAGE_SIZE"));
    }

    @Test
    @DisplayName("DELETE /api/v1/foods/{foodId} — 정상 삭제 시 204를 반환한다.")
    void deleteFood_returns204() throws Exception {
        // given
        Long memberId = 1L;
        Long foodId = 10L;
        willDoNothing().given(foodFacade).deleteFood(memberId, foodId);

        // when & then
        mockMvc.perform(delete("/api/v1/foods/{foodId}", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("DELETE /api/v1/foods/{foodId} — 존재하지 않는 음식이면 404를 반환한다.")
    void deleteFood_returns404() throws Exception {
        // given
        Long memberId = 1L;
        Long foodId = 99L;
        willThrow(new FoodNotFoundException())
                .given(foodFacade).deleteFood(memberId, foodId);

        // when & then
        mockMvc.perform(delete("/api/v1/foods/{foodId}", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("FOOD_NOT_FOUND"));
    }

    @Test
    @DisplayName("DELETE /api/v1/foods/{foodId} — 다른 회원의 음식 삭제 시 403을 반환한다.")
    void deleteFood_returns403() throws Exception {
        // given
        Long memberId = 2L;
        Long foodId = 10L;
        willThrow(new FoodForbiddenException())
                .given(foodFacade).deleteFood(memberId, foodId);

        // when & then
        mockMvc.perform(delete("/api/v1/foods/{foodId}", foodId)
                        .session(sessionOf(memberId)))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FOOD_FORBIDDEN"));
    }

    @Test
    @DisplayName("capacity가 0이면 400과 검증 오류 메시지를 반환한다.")
    void registerFood_Returns400_WhenCapacityIsZero() throws Exception {
        // given
        Long memberId = 1L;
        FoodCreateRequest request = new FoodCreateRequest("초코 스무디", 0, "상세 내용", null, LocalDate.now().plusDays(7));

        MockMultipartFile expiredImage = new MockMultipartFile(
                "expiredImage", "exp.png", "image/png", "exp-content".getBytes()
        );
        MockMultipartFile requestPart = new MockMultipartFile(
                "request", "", MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(request)
        );

        // when & then
        mockMvc.perform(multipart("/api/v1/foods")
                        .file(expiredImage)
                        .file(requestPart)
                        .session(sessionOf(memberId))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/foods/expired-date — 소비기한 사진을 올리면 인식된 날짜(expired)를 반환한다.")
    void recognizeExpirationDate_returnsExpired() throws Exception {
        // given
        Long memberId = 1L;
        LocalDate recognized = LocalDate.of(2030, 12, 31);
        MockMultipartFile expiredImage = new MockMultipartFile(
                "expiredImage", "exp.png", "image/png", "exp-content".getBytes()
        );
        given(foodFacade.recognizeExpirationDate(any())).willReturn(recognized);

        // when & then
        mockMvc.perform(multipart("/api/v1/foods/expired-date")
                        .file(expiredImage)
                        .session(sessionOf(memberId))
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expired").value("2030-12-31"))
                .andExpect(jsonPath("$.message").value("소비기한 분석이 완료되었습니다."));
    }
}
