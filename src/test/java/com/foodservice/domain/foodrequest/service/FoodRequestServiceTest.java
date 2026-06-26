package com.foodservice.domain.foodrequest.service;

import com.foodservice.common.exception.foodrequest.FoodRequestAlreadyExistsException;
import com.foodservice.common.exception.foodrequest.FoodRequestNotFoundException;
import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.entity.FoodRequestStatus;
import com.foodservice.domain.foodrequest.repository.FoodRequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import java.util.Optional;

import static com.foodservice.domain.foodrequest.entity.FoodRequestStatus.REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodRequestServiceTest {

    @Mock
    private FoodRequestRepository foodRequestRepository;

    @InjectMocks
    private FoodRequestService foodRequestService;

    @Test
    @DisplayName("신청이 없으면 새 FoodRequest가 저장된다.")
    void createRequest_success() {
        // given
        Long memberId = 2L;
        Long foodId = 1L;
        given(foodRequestRepository.existsDuplicateRequest(foodId, memberId, REQUEST))
                .willReturn(false);
        FoodRequest saved = FoodRequest.builder().foodId(foodId).memberId(memberId).build();
        given(foodRequestRepository.save(any(FoodRequest.class))).willReturn(saved);

        // when
        foodRequestService.createRequest(memberId, foodId);

        // then
        verify(foodRequestRepository).save(any(FoodRequest.class));
    }

    @Test
    @DisplayName("이미 REQUEST 상태 신청이 있으면 FoodRequestAlreadyExistsException이 발생한다.")
    void createRequest_throws_whenAlreadyExists() {
        // given
        given(foodRequestRepository.existsDuplicateRequest(1L, 2L, REQUEST))
                .willReturn(true);

        // when & then
        assertThatThrownBy(() -> foodRequestService.createRequest(2L, 1L))
                .isInstanceOf(FoodRequestAlreadyExistsException.class);
    }

    @Test
    @DisplayName("존재하지 않는 requestId로 조회 시 FoodRequestNotFoundException이 발생한다.")
    void getRequest_throws_whenNotFound() {
        // given
        given(foodRequestRepository.findActiveById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> foodRequestService.getRequest(99L))
                .isInstanceOf(FoodRequestNotFoundException.class);
    }

    @Test
    @DisplayName("approve() 호출 시 FoodRequest의 status가 APPROVED로 변경된다.")
    void approve_changesStatusToApproved() {
        // given
        FoodRequest request = FoodRequest.builder().foodId(1L).memberId(2L).build();
        given(foodRequestRepository.findActiveById(1L)).willReturn(Optional.of(request));

        // when
        foodRequestService.approve(1L);

        // then
        assertThat(request.getStatus()).isEqualTo(FoodRequestStatus.APPROVED);
    }

    @Test
    @DisplayName("rejectPendingForExpiredFoods() 호출 시 repository 메서드가 실행된다.")
    void rejectPendingForExpiredFoods_delegatesToRepository() {
        // given
        given(foodRequestRepository.rejectPendingForExpiredFoods()).willReturn(5);

        // when
        int result = foodRequestService.rejectPendingForExpiredFoods();

        // then
        assertThat(result).isEqualTo(5);
        verify(foodRequestRepository, times(1)).rejectPendingForExpiredFoods();
    }

    @Test
    @DisplayName("reject() 호출 시 FoodRequest의 status가 REJECTED로 변경된다.")
    void reject_changesStatusToRejected() {
        // given
        FoodRequest request = FoodRequest.builder().foodId(1L).memberId(2L).build();
        given(foodRequestRepository.findActiveById(1L)).willReturn(Optional.of(request));

        // when
        foodRequestService.reject(1L);

        // then
        assertThat(request.getStatus()).isEqualTo(FoodRequestStatus.REJECTED);
    }

    @Test
    @DisplayName("getRequestsByFoodId() - foodId에 해당하는 신청 목록을 반환한다.")
    void getRequestsByFoodId_returnsList() {
        // given
        List<FoodRequest> requests = List.of(
                FoodRequest.builder().foodId(1L).memberId(2L).build(),
                FoodRequest.builder().foodId(1L).memberId(3L).build()
        );
        given(foodRequestRepository.findActiveByFoodId(1L))
                .willReturn(requests);

        // when
        List<FoodRequest> result = foodRequestService.getRequestsByFoodId(1L);

        // then
        assertThat(result).hasSize(2);
        verify(foodRequestRepository).findActiveByFoodId(1L);
    }

    @Test
    @DisplayName("getMyRequest() — 활성 신청이 있으면 반환한다.")
    void getMyRequest_returnsRequest() {
        // given
        FoodRequest request = FoodRequest.builder().foodId(1L).memberId(2L).build();
        given(foodRequestRepository.findActiveByFoodIdAndMemberId(1L, 2L, PageRequest.of(0, 1))).willReturn(List.of(request));

        // when
        FoodRequest result = foodRequestService.getMyRequest(2L, 1L);

        // then
        assertThat(result.getMemberId()).isEqualTo(2L);
        verify(foodRequestRepository).findActiveByFoodIdAndMemberId(1L, 2L, PageRequest.of(0, 1));
    }

    @Test
    @DisplayName("getMyRequest() — 신청이 없으면 FoodRequestNotFoundException이 발생한다.")
    void getMyRequest_throws_whenNotFound() {
        // given
        given(foodRequestRepository.findActiveByFoodIdAndMemberId(1L, 2L, PageRequest.of(0, 1))).willReturn(List.of());

        // when & then
        assertThatThrownBy(() -> foodRequestService.getMyRequest(2L, 1L))
                .isInstanceOf(FoodRequestNotFoundException.class);
    }
}
