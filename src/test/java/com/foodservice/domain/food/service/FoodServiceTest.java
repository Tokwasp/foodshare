package com.foodservice.domain.food.service;

import com.foodservice.common.exception.food.FoodForbiddenException;
import com.foodservice.common.exception.food.FoodLimitExceededException;
import com.foodservice.common.exception.food.FoodNotFoundException;
import com.foodservice.domain.food.dto.FoodCreateRequest;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import java.util.Optional;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock
    private FoodRepository foodRepository;

    @InjectMocks
    private FoodService foodService;

    @Test
    @DisplayName("유효한 데이터가 주어지면 음식 물품이 정상적으로 등록된다.")
    void registerFood_Success() {
        // given
        Long memberId = 1L;
        FoodCreateRequest request = new FoodCreateRequest("초코 바른 초코 스무디", 3, "details", null, LocalDate.now().plusDays(7));
        LocalDateTime expired = LocalDateTime.now().plusDays(7);

        given(foodRepository.findActiveByMemberIdAndStatus(memberId, IN_PROGRESS))
                .willReturn(List.of());

        Food savedFood = Food.builder()
                .memberId(memberId)
                .foodName(request.getFoodName())
                .details(request.getDetails())
                .capacity(request.getCapacity())
                .expired(expired)
                .build();
        given(foodRepository.save(any(Food.class))).willReturn(savedFood);

        // when
        foodService.registerFood(memberId, request, expired);

        // then
        verify(foodRepository, times(1)).save(any(Food.class));
    }

    @Test
    @DisplayName("활성 음식이 10개 이상이면 FoodLimitExceededException이 발생한다.")
    void registerFood_ThrowsException_WhenActiveFoodLimitExceeded() {
        // given
        Long memberId = 1L;
        FoodCreateRequest request = new FoodCreateRequest("신규 음식", 2, "details", null, LocalDate.now().plusDays(7));
        LocalDateTime expired = LocalDateTime.now().plusDays(7);

        Food activeFood = Food.builder()
                .memberId(memberId)
                .foodName("기존 음식")
                .details("details")
                .capacity(1)
                .expired(expired)
                .build();
        given(foodRepository.findActiveByMemberIdAndStatus(memberId, IN_PROGRESS))
                .willReturn(java.util.Collections.nCopies(10, activeFood));

        // when & then
        assertThatThrownBy(() -> foodService.registerFood(memberId, request, expired))
                .isInstanceOf(FoodLimitExceededException.class);

        verify(foodRepository, times(0)).save(any(Food.class));
    }

    @Test
    @DisplayName("활성 음식이 9개면(한도 미만) 음식이 정상 등록된다.")
    void registerFood_Success_WhenActiveFoodBelowLimit() {
        // given
        Long memberId = 1L;
        FoodCreateRequest request = new FoodCreateRequest("신규 음식", 2, "details", null, LocalDate.now().plusDays(7));
        LocalDateTime expired = LocalDateTime.now().plusDays(7);

        Food activeFood = Food.builder()
                .memberId(memberId)
                .foodName("기존 음식")
                .details("details")
                .capacity(1)
                .expired(expired)
                .build();
        given(foodRepository.findActiveByMemberIdAndStatus(memberId, IN_PROGRESS))
                .willReturn(java.util.Collections.nCopies(9, activeFood));
        given(foodRepository.save(any(Food.class))).willReturn(activeFood);

        // when
        foodService.registerFood(memberId, request, expired);

        // then
        verify(foodRepository, times(1)).save(any(Food.class));
    }

    @Test
    @DisplayName("존재하는 foodId로 조회하면 Food를 반환한다.")
    void getFood_returnsFood() {
        // given
        Long foodId = 1L;
        Food food = Food.builder()
                .memberId(1L).foodName("음식").details("d").capacity(2)
                .expired(LocalDateTime.now().plusDays(7))
                .build();
        given(foodRepository.findActiveById(foodId)).willReturn(Optional.of(food));

        // when
        Food result = foodService.getFood(foodId);

        // then
        assertThat(result.getFoodName()).isEqualTo("음식");
    }

    @Test
    @DisplayName("존재하지 않는 foodId로 조회하면 FoodNotFoundException이 발생한다.")
    void getFood_throwsException_whenNotFound() {
        // given
        given(foodRepository.findActiveById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> foodService.getFood(99L))
                .isInstanceOf(FoodNotFoundException.class);
    }

    @Test
    @DisplayName("IN_PROGRESS 음식 목록을 페이징하여 반환한다.")
    void getFoodPage_returnsPagedFoods() {
        // given
        LocalDateTime expired = LocalDateTime.now().plusDays(7);
        Food food = Food.builder()
                .memberId(1L).foodName("음식").details("d").capacity(2).expired(expired)
                .build();
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Food> foodPage = new PageImpl<>(List.of(food), pageable, 1);
        given(foodRepository.findPageByStatus(IN_PROGRESS, pageable)).willReturn(foodPage);

        // when
        Page<Food> result = foodService.getFoodPage(IN_PROGRESS, pageable);

        // then
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getFoodName()).isEqualTo("음식");
    }

    @Test
    @DisplayName("status가 null이면 전체 활성 음식을 페이징 조회한다.")
    void getFoodPage_nullStatus_returnsAllActive() {
        // given
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Food> foodPage = new PageImpl<>(List.of(), pageable, 0);
        given(foodRepository.findActivePage(pageable)).willReturn(foodPage);

        // when
        Page<Food> result = foodService.getFoodPage(null, pageable);

        // then
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("등록자가 음식을 삭제하면 food.delete()가 호출된다.")
    void deleteFood_success() {
        // given
        Long memberId = 1L;
        Long foodId = 10L;
        Food food = Food.builder()
                .memberId(memberId).foodName("음식").details("d").capacity(2)
                .expired(LocalDateTime.now().plusDays(7))
                .build();
        given(foodRepository.findActiveById(foodId)).willReturn(Optional.of(food));

        // when
        foodService.deleteFood(memberId, foodId);

        // then
        assertThat(food.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("다른 회원이 삭제 시도하면 FoodForbiddenException이 발생한다.")
    void deleteFood_throws_whenNotOwner() {
        // given
        Long ownerId = 1L;
        Long otherId = 2L;
        Long foodId = 10L;
        Food food = Food.builder()
                .memberId(ownerId).foodName("음식").details("d").capacity(2)
                .expired(LocalDateTime.now().plusDays(7))
                .build();
        given(foodRepository.findActiveById(foodId)).willReturn(Optional.of(food));

        // when & then
        assertThatThrownBy(() -> foodService.deleteFood(otherId, foodId))
                .isInstanceOf(FoodForbiddenException.class);
    }

    @Test
    @DisplayName("존재하지 않는 음식 삭제 시 FoodNotFoundException이 발생한다.")
    void deleteFood_throws_whenNotFound() {
        // given
        given(foodRepository.findActiveById(99L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> foodService.deleteFood(1L, 99L))
                .isInstanceOf(FoodNotFoundException.class);
    }

    @Test
    @DisplayName("bulkExpire 호출 시 repository의 bulkExpire가 실행되고 건수를 반환한다.")
    void bulkExpire_delegatesToRepositoryAndReturnsCount() {
        // given
        LocalDateTime now = LocalDateTime.now();
        given(foodRepository.bulkExpire(now)).willReturn(3);

        // when
        int result = foodService.bulkExpire(now);

        // then
        assertThat(result).isEqualTo(3);
        verify(foodRepository, times(1)).bulkExpire(now);
    }
}
