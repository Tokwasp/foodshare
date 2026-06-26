package com.foodservice.domain.food.service;

import com.foodservice.common.exception.food.FoodForbiddenException;
import com.foodservice.common.exception.food.FoodLimitExceededException;
import com.foodservice.common.exception.food.FoodNotFoundException;
import com.foodservice.domain.food.dto.FoodCreateRequest;
import com.foodservice.domain.food.entity.ExStatus;
import com.foodservice.domain.food.entity.Food;
import com.foodservice.domain.food.repository.FoodRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static com.foodservice.domain.food.entity.ExStatus.IN_PROGRESS;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodService {

    private static final int ACTIVE_FOOD_LIMIT = 10;

    private final FoodRepository foodRepository;

    @Transactional
    public Long registerFood(Long memberId, FoodCreateRequest request, LocalDateTime expired) {
        validateActiveFoodLimit(memberId);

        Food food = Food.builder()
                .memberId(memberId)
                .foodName(request.getFoodName())
                .details(request.getDetails())
                .capacity(request.getCapacity())
                .expired(expired)
                .region(request.getRegion())
                .build();

        return foodRepository.save(food).getFoodId();
    }

    public Food getFood(Long foodId) {
        return foodRepository.findActiveById(foodId)
                .orElseThrow(FoodNotFoundException::new);
    }

    public Page<Food> getFoodPage(ExStatus status, Pageable pageable) {
        if (status == null) {
            return foodRepository.findActivePage(pageable);
        }
        return foodRepository.findPageByStatus(status, pageable);
    }

    public List<Food> getMyFoods(Long memberId) {
        return foodRepository.findActiveByMemberId(memberId);
    }

    // 메인 화면 노출용: 가장 최근에 등록된 음식 N개 (삭제 제외, foodId 내림차순 = 등록 최신순)
    public List<Food> getRecentFoods(int size) {
        return foodRepository
                .findActivePage(PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "foodId")))
                .getContent();
    }

    // 삭제 여부와 무관하게 id로 일괄 조회 (보낸 요청 목록의 물품명 표시용 — 등록자가 삭제한 물품도 표시)
    public List<Food> getFoodsByIds(List<Long> foodIds) {
        return foodRepository.findAllById(foodIds);
    }

    @Transactional
    public void deleteFood(Long memberId, Long foodId) {
        Food food = getFood(foodId);
        if (!food.getMemberId().equals(memberId)) {
            throw new FoodForbiddenException();
        }
        food.delete();
    }

    @Transactional
    public int bulkExpire(LocalDateTime now) {
        return foodRepository.bulkExpire(now);
    }

    private void validateActiveFoodLimit(Long memberId) {
        int activeCount = foodRepository
                .findActiveByMemberIdAndStatus(memberId, IN_PROGRESS)
                .size();
        if (activeCount >= ACTIVE_FOOD_LIMIT) {
            throw new FoodLimitExceededException();
        }
    }
}
