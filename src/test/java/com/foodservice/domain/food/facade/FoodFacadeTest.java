package com.foodservice.domain.food.facade;

import com.foodservice.common.exception.food.ExpiredImageRequiredException;
import com.foodservice.common.exception.food.FoodForbiddenException;
import com.foodservice.common.exception.food.FoodNotAvailableException;
import com.foodservice.domain.food.client.ExpirationApiClient;
import com.foodservice.domain.food.dto.FoodCreateRequest;
import com.foodservice.domain.foodrequest.dto.FoodRequestListResponse;
import com.foodservice.domain.foodrequest.dto.MyFoodRequestResponse;
import com.foodservice.domain.foodrequest.dto.FoodRequestResponse;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.service.FoodRequestService;
import com.foodservice.domain.food.service.FoodService;
import com.foodservice.domain.image.dto.response.ImageUploadResponse;
import com.foodservice.domain.image.service.ImageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static com.foodservice.domain.image.entity.ImageType.BASIC;
import static com.foodservice.domain.image.entity.ImageType.EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodFacadeTest {

    @Mock
    private FoodService foodService;

    @Mock
    private FoodRequestService foodRequestService;

    @Mock
    private ImageService imageService;

    @Mock
    private com.foodservice.domain.member.service.MemberService memberService;

    @Mock
    private ExpirationApiClient expirationApiClient;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private FoodFacade foodFacade;

    @Test
    @DisplayName("음식 등록 시 소비기한 사진(EXPIRED)과 물품 사진들(BASIC)이 업로드되고 트랜잭션 안에서 원자적으로 저장된다.")
    void registerFood_uploadsExpiredAndBasicImages_thenSavesAtomically() {
        // given
        Long memberId = 1L;
        Long expectedFoodId = 10L;
        LocalDate recognized = LocalDate.now().plusDays(7);
        FoodCreateRequest request = new FoodCreateRequest("초코 스무디", 3, "details", null, recognized);

        MockMultipartFile expiredImage = new MockMultipartFile(
                "expiredImage", "exp.png", "image/png", "exp-content".getBytes()
        );
        MockMultipartFile basicImage = new MockMultipartFile(
                "images", "basic.png", "image/png", "basic-content".getBytes()
        );

        LocalDateTime expectedExpired = recognized.minusDays(1).atStartOfDay();
        ImageUploadResponse expiredUpload = new ImageUploadResponse("https://cdn/exp.png", "stored-exp.png");
        ImageUploadResponse basicUpload = new ImageUploadResponse("https://cdn/basic.png", "stored-basic.png");

        given(imageService.uploadToStorage(expiredImage)).willReturn(expiredUpload);
        given(imageService.uploadToStorage(basicImage)).willReturn(basicUpload);
        given(foodService.registerFood(eq(memberId), eq(request), eq(expectedExpired)))
                .willReturn(expectedFoodId);

        // TransactionTemplate.execute()가 람다를 실제로 실행하도록 설정
        given(transactionTemplate.execute(any())).willAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        // when
        Long foodId = foodFacade.registerFood(memberId, request, expiredImage, List.of(basicImage));

        // then
        assertThat(foodId).isEqualTo(expectedFoodId);
        verify(foodService, times(1)).registerFood(eq(memberId), eq(request), eq(expectedExpired));
        verify(imageService, times(1)).saveImageMeta(
                eq(expectedFoodId), eq(expiredUpload), eq("exp.png"), eq(EXPIRED)
        );
        verify(imageService, times(1)).saveImageMeta(
                eq(expectedFoodId), eq(basicUpload), eq("basic.png"), eq(BASIC)
        );
    }

    @Test
    @DisplayName("음식 등록 시 소비기한 사진이 없으면 ExpiredImageRequiredException이 발생한다.")
    void registerFood_throws_whenExpiredImageMissing() {
        // given
        FoodCreateRequest request = new FoodCreateRequest("초코 스무디", 3, "details", null, LocalDate.now().plusDays(7));

        // when & then
        assertThatThrownBy(() -> foodFacade.registerFood(1L, request, null, List.of()))
                .isInstanceOf(ExpiredImageRequiredException.class);
        verify(foodService, times(0)).registerFood(any(), any(), any());
    }

    @Test
    @DisplayName("소비기한 인식 시 파일 검증 후 AI가 인식한 날짜를 반환한다.")
    void recognizeExpirationDate_returnsAiDate() {
        // given
        MockMultipartFile expiredImage = new MockMultipartFile(
                "expiredImage", "exp.png", "image/png", "exp-content".getBytes()
        );
        LocalDate recognized = LocalDate.now().plusDays(5);
        willDoNothing().given(imageService).validateImageFile(expiredImage);
        given(expirationApiClient.fetchExpirationDate(expiredImage)).willReturn(recognized);

        // when
        LocalDate result = foodFacade.recognizeExpirationDate(expiredImage);

        // then
        assertThat(result).isEqualTo(recognized);
        verify(imageService, times(1)).validateImageFile(expiredImage);
    }

    @Test
    @DisplayName("requestFood — 정상 신청 시 FoodRequestResponse를 반환한다.")
    void requestFood_success() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Long requestId = 10L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        given(foodService.getFood(foodId)).willReturn(food);
        given(foodRequestService.createRequest(memberId, foodId)).willReturn(requestId);

        // when
        FoodRequestResponse response = foodFacade.requestFood(memberId, foodId);

        // then
        assertThat(response.requestFoodId()).isEqualTo(requestId);
        verify(foodRequestService).createRequest(memberId, foodId);
    }

    @Test
    @DisplayName("requestFood — 본인 음식에 신청 시 FoodForbiddenException이 발생한다.")
    void requestFood_throws_whenOwnFood() {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        given(foodService.getFood(foodId)).willReturn(food);

        // when & then
        assertThatThrownBy(() -> foodFacade.requestFood(memberId, foodId))
                .isInstanceOf(FoodForbiddenException.class);
    }

    @Test
    @DisplayName("requestFood — IN_PROGRESS가 아닌 음식 신청 시 FoodNotAvailableException이 발생한다.")
    void requestFood_throws_whenFoodNotAvailable() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .exStatus(ExStatus.COMPLETED).expired(LocalDateTime.now().plusDays(7)).build();
        given(foodService.getFood(foodId)).willReturn(food);

        // when & then
        assertThatThrownBy(() -> foodFacade.requestFood(memberId, foodId))
                .isInstanceOf(FoodNotAvailableException.class);
    }

    @Test
    @DisplayName("approveRequest — 등록자가 승인 시 approve + incrementApprovedCount가 호출된다.")
    void approveRequest_success() {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Long requestId = 10L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        FoodRequest request = FoodRequest.builder().foodId(foodId).memberId(2L).build();
        given(foodService.getFood(foodId)).willReturn(food);
        given(foodRequestService.getRequest(requestId)).willReturn(request);

        // when
        foodFacade.approveRequest(memberId, foodId, requestId);

        // then
        verify(foodRequestService).getRequest(requestId);
        assertThat(food.getApprovedCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("approveRequest — 등록자가 아닌 경우 FoodForbiddenException이 발생한다.")
    void approveRequest_throws_whenNotOwner() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        given(foodService.getFood(foodId)).willReturn(food);

        // when & then
        assertThatThrownBy(() -> foodFacade.approveRequest(memberId, foodId, 10L))
                .isInstanceOf(FoodForbiddenException.class);
    }

    @Test
    @DisplayName("approveRequest — 만료된 음식에 승인 시 FoodNotAvailableException이 발생한다.")
    void approveRequest_throws_whenFoodExpired() {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .exStatus(ExStatus.EXPIRED).expired(LocalDateTime.now().minusDays(1)).build();
        given(foodService.getFood(foodId)).willReturn(food);

        // when & then
        assertThatThrownBy(() -> foodFacade.approveRequest(memberId, foodId, 10L))
                .isInstanceOf(FoodNotAvailableException.class);
    }

    @Test
    @DisplayName("bulkExpire — foodService.bulkExpire와 foodRequestService.rejectPendingForExpiredFoods가 순서대로 호출된다.")
    void bulkExpire_callsBothServicesInOrder() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(foodService.bulkExpire(now)).willReturn(2);
        given(foodRequestService.rejectPendingForExpiredFoods()).willReturn(3);

        // when
        foodFacade.bulkExpire(now);

        // then
        verify(foodService).bulkExpire(now);
        verify(foodRequestService).rejectPendingForExpiredFoods();
    }

    @Test
    @DisplayName("rejectRequest — 등록자가 거절 시 reject가 호출된다.")
    void rejectRequest_success() {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Long requestId = 10L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        FoodRequest request = FoodRequest.builder().foodId(foodId).memberId(2L).build();
        given(foodService.getFood(foodId)).willReturn(food);
        given(foodRequestService.getRequest(requestId)).willReturn(request);

        // when
        foodFacade.rejectRequest(memberId, foodId, requestId);

        // then
        verify(foodRequestService).getRequest(requestId);
    }

    @Test
    @DisplayName("deleteFood 호출 시 foodService와 imageService가 모두 호출된다.")
    void deleteFood_callsBothServices() {
        // given
        Long memberId = 1L;
        Long foodId = 10L;
        willDoNothing().given(foodService).deleteFood(memberId, foodId);
        willDoNothing().given(imageService).deleteImagesByFoodId(foodId);

        // when
        foodFacade.deleteFood(memberId, foodId);

        // then
        verify(foodService, times(1)).deleteFood(memberId, foodId);
        verify(imageService, times(1)).deleteImagesByFoodId(foodId);
    }

    @Test
    @DisplayName("getRequests — 등록자가 조회 시 신청 목록을 반환한다.")
    void getRequests_returnsListForOwner() {
        // given
        Long memberId = 1L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        List<FoodRequest> requests = List.of(
                FoodRequest.builder().foodId(foodId).memberId(2L).build(),
                FoodRequest.builder().foodId(foodId).memberId(3L).build()
        );
        given(foodService.getFood(foodId)).willReturn(food);
        given(foodRequestService.getRequestsByFoodId(foodId)).willReturn(requests);
        given(memberService.getNickNames(org.mockito.ArgumentMatchers.anyList()))
                .willReturn(java.util.Map.of(2L, "신청자A", 3L, "신청자B"));

        // when
        List<FoodRequestListResponse> result = foodFacade.getRequests(memberId, foodId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).requesterNickName()).isEqualTo("신청자A");
        verify(foodRequestService).getRequestsByFoodId(foodId);
    }

    @Test
    @DisplayName("getRequests — 등록자가 아닌 경우 FoodForbiddenException이 발생한다.")
    void getRequests_throws_whenNotOwner() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        given(foodService.getFood(foodId)).willReturn(food);

        // when & then
        assertThatThrownBy(() -> foodFacade.getRequests(memberId, foodId))
                .isInstanceOf(FoodForbiddenException.class);
    }

    @Test
    @DisplayName("cancelRequest — 신청자 본인이 REQUEST 상태 신청을 취소하면 deleted=true가 된다.")
    void cancelRequest_success() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Long requestId = 10L;
        FoodRequest request = FoodRequest.builder().foodId(foodId).memberId(memberId).build();
        given(foodRequestService.getRequest(requestId)).willReturn(request);

        // when
        foodFacade.cancelRequest(memberId, foodId, requestId);

        // then
        assertThat(request.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("cancelRequest — 신청자 본인이 아닌 경우 FoodForbiddenException이 발생한다.")
    void cancelRequest_throws_whenNotRequester() {
        // given
        Long memberId = 3L;
        Long foodId = 1L;
        Long requestId = 10L;
        FoodRequest request = FoodRequest.builder().foodId(foodId).memberId(2L).build();
        given(foodRequestService.getRequest(requestId)).willReturn(request);

        // when & then
        assertThatThrownBy(() -> foodFacade.cancelRequest(memberId, foodId, requestId))
                .isInstanceOf(FoodForbiddenException.class);
    }

    @Test
    @DisplayName("cancelRequest — foodId가 일치하지 않으면 FoodRequestMismatchException이 발생한다.")
    void cancelRequest_throws_whenMismatch() {
        // given
        Long memberId = 2L;
        Long foodId = 99L;
        Long requestId = 10L;
        FoodRequest request = FoodRequest.builder().foodId(1L).memberId(memberId).build();
        given(foodRequestService.getRequest(requestId)).willReturn(request);

        // when & then
        assertThatThrownBy(() -> foodFacade.cancelRequest(memberId, foodId, requestId))
                .isInstanceOf(com.foodservice.common.exception.foodrequest.FoodRequestMismatchException.class);
    }

    @Test
    @DisplayName("getMyRequest — 신청자가 본인 신청 상태를 조회할 수 있다.")
    void getMyRequest_returnsMyRequest() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        FoodRequest request = FoodRequest.builder().foodId(foodId).memberId(memberId).build();
        given(foodService.getFood(foodId)).willReturn(food);
        given(foodRequestService.getMyRequest(memberId, foodId)).willReturn(request);

        // when
        MyFoodRequestResponse response = foodFacade.getMyRequest(memberId, foodId);

        // then
        assertThat(response.status()).isEqualTo(com.foodservice.domain.foodrequest.entity.FoodRequestStatus.REQUEST);
        verify(foodRequestService).getMyRequest(memberId, foodId);
    }

    @Test
    @DisplayName("getMyRequest — 신청이 없으면 FoodRequestNotFoundException이 발생한다.")
    void getMyRequest_throws_whenNotFound() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        Food food = Food.builder().memberId(1L).foodName("음식").details("d").capacity(3)
                .expired(LocalDateTime.now().plusDays(7)).build();
        given(foodService.getFood(foodId)).willReturn(food);
        given(foodRequestService.getMyRequest(memberId, foodId))
                .willThrow(new com.foodservice.common.exception.foodrequest.FoodRequestNotFoundException());

        // when & then
        assertThatThrownBy(() -> foodFacade.getMyRequest(memberId, foodId))
                .isInstanceOf(com.foodservice.common.exception.foodrequest.FoodRequestNotFoundException.class);
    }
}
