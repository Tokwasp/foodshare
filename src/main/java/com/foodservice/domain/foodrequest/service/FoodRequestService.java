package com.foodservice.domain.foodrequest.service;

import com.foodservice.common.exception.foodrequest.FoodRequestAlreadyExistsException;
import com.foodservice.common.exception.foodrequest.FoodRequestNotFoundException;
import com.foodservice.domain.foodrequest.entity.FoodRequest;
import com.foodservice.domain.foodrequest.repository.FoodRequestRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.foodservice.domain.foodrequest.entity.FoodRequestStatus.REQUEST;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FoodRequestService {

    private final FoodRequestRepository foodRequestRepository;

    @Transactional
    public Long createRequest(Long memberId, Long foodId) {
        if (foodRequestRepository.existsDuplicateRequest(foodId, memberId, REQUEST)) {
            throw new FoodRequestAlreadyExistsException();
        }
        FoodRequest request = FoodRequest.builder()
                .foodId(foodId)
                .memberId(memberId)
                .build();
        return foodRequestRepository.save(request).getRequestFoodId();
    }

    public FoodRequest getRequest(Long requestId) {
        return foodRequestRepository.findActiveById(requestId)
                .orElseThrow(FoodRequestNotFoundException::new);
    }

    @Transactional
    public void approve(Long requestId) {
        getRequest(requestId).approve();
    }

    @Transactional
    public void reject(Long requestId) {
        getRequest(requestId).reject();
    }

    @Transactional
    public int rejectPendingForExpiredFoods() {
        return foodRequestRepository.rejectPendingForExpiredFoods();
    }

    @Transactional
    public int rejectPendingByFoodId(Long foodId) {
        return foodRequestRepository.rejectPendingByFoodId(foodId);
    }

    public FoodRequest getMyRequest(Long memberId, Long foodId) {
        return foodRequestRepository.findActiveByFoodIdAndMemberId(foodId, memberId, PageRequest.of(0, 1))
                .stream().findFirst()
                .orElseThrow(FoodRequestNotFoundException::new);
    }

    public List<FoodRequest> getRequestsByFoodId(Long foodId) {
        return foodRequestRepository.findActiveByFoodId(foodId);
    }

    public List<FoodRequest> getMySentRequests(Long memberId) {
        return foodRequestRepository.findActiveByMemberId(memberId);
    }
}
